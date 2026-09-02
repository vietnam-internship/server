#!/usr/bin/env python3
"""
TimeSlotInventoryReconciler가 감지하는 위반(슬롯 잔여 정원 > 지점 정원)을 수동으로 복구하는
인시던트 대응 스크립트.

기본 동작은 dry-run이다 — 실제로 UPDATE를 실행하려면 --apply를 명시해야 한다.
락은 애플리케이션의 BranchTimeSlotRepository.lockForUpdate와 동일하게
`SELECT ... FOR UPDATE`를 잡고, 그 트랜잭션 안에서 재확인 후 UPDATE까지 커밋한다 —
스크립트가 도는 동안에도 서비스 트래픽이 같은 행을 건드릴 수 있으므로, 이 락 없이는
"고치는 도중에 또 꼬이는" 레이스가 재발할 수 있다.

사용 예:
    # 전체 스캔, dry-run(기본값) — 무엇이 바뀔지만 출력
    python3 scripts/fix_inventory_overcapacity.py

    # 전체 스캔, 실제로 적용
    python3 scripts/fix_inventory_overcapacity.py --apply

    # 특정 슬롯 하나만 — slotId로 지정(디스코드 알림/로그에 찍힌 값)
    python3 scripts/fix_inventory_overcapacity.py --slot-id 1234 --apply

    # 특정 슬롯 하나만 — 환전소·날짜·시간으로 지정(사람이 알아보기 쉬운 방식)
    python3 scripts/fix_inventory_overcapacity.py --branch-id 3 --date 2026-08-20 --time 14:30 --apply

환경 변수:
    DB_HOST, DB_PORT(기본 3306), DB_NAME, DB_USER, DB_PASSWORD
"""

import argparse
import logging
import os
import sys
from datetime import date

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-5s | %(message)s",
)
log = logging.getLogger("fix_inventory_overcapacity")


def connect():
    try:
        import pymysql
        import pymysql.cursors
    except ImportError:
        sys.exit("pymysql이 필요합니다: pip install pymysql")

    required = ["DB_HOST", "DB_NAME", "DB_USER", "DB_PASSWORD"]
    missing = [k for k in required if not os.environ.get(k)]
    if missing:
        sys.exit(f"환경 변수 누락: {', '.join(missing)}")

    return pymysql.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", "3306")),
        db=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def resolve_slot_id(conn, branch_id, slot_date, slot_time):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM branch_time_slots WHERE branch_id = %s AND slot_date = %s AND slot_time = %s",
            [branch_id, slot_date, slot_time],
        )
        row = cur.fetchone()
        return row["id"] if row else None


def find_violations(conn, slot_id=None):
    """
    TimeSlotInventoryReconciler.reconcile()과 같은 조건(오늘 이후 슬롯, 활성 지점,
    remaining > capacity)으로 위반 슬롯을 찾는다. 여기서는 락을 잡지 않는다 —
    대상 목록만 뽑는 단계라 fix_one()에서 행별로 다시 잠그고 재확인한다.
    """
    sql = """
        SELECT s.id AS slot_id, s.branch_id, s.slot_date, s.slot_time,
               s.remaining, b.time_slot_capacity AS capacity
        FROM branch_time_slots s
        JOIN branches b ON b.id = s.branch_id
        WHERE b.active = 1
          AND s.slot_date >= %s
          AND s.remaining > b.time_slot_capacity
    """
    params = [date.today()]
    if slot_id is not None:
        sql += " AND s.id = %s"
        params.append(slot_id)

    with conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchall()


def fix_one(conn, slot_id, apply_changes):
    """
    행 하나를 잠그고(FOR UPDATE) 그 시점의 최신 값으로 재확인한 뒤, 정원까지 감소시킨다.
    스캔 시점과 수정 시점 사이에 실제 트래픽이 이 행을 이미 정상으로 되돌렸을 수도 있으므로,
    잠근 뒤 다시 remaining > capacity인지 확인하고, 아니면 그냥 건너뛴다(멱등).
    """
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT s.remaining, b.time_slot_capacity AS capacity
            FROM branch_time_slots s
            JOIN branches b ON b.id = s.branch_id
            WHERE s.id = %s
            FOR UPDATE
            """,
            [slot_id],
        )
        row = cur.fetchone()
        if row is None:
            log.warning("slotId=%s 존재하지 않음(다른 경로에서 삭제됐을 수 있음) — 건너뜀", slot_id)
            conn.rollback()
            return False

        remaining, capacity = row["remaining"], row["capacity"]
        if remaining <= capacity:
            log.info(
                "slotId=%s remaining=%d capacity=%d — 이미 정상, 건너뜀",
                slot_id, remaining, capacity,
            )
            conn.rollback()
            return False

        target = capacity
        log.info(
            "slotId=%s remaining %d -> %d (capacity=%d, 초과분 %d 제거)",
            slot_id, remaining, target, capacity, remaining - target,
        )

        if not apply_changes:
            conn.rollback()
            return True

        cur.execute(
            "UPDATE branch_time_slots SET remaining = %s WHERE id = %s AND remaining = %s",
            [target, slot_id, remaining],
        )
        if cur.rowcount != 1:
            log.error("slotId=%s UPDATE가 예상과 다르게 적용됨(rowcount=%d) — 롤백", slot_id, cur.rowcount)
            conn.rollback()
            return False

        conn.commit()
        log.info("slotId=%s 적용 완료", slot_id)
        return True


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--slot-id", type=int, default=None,
                         help="특정 슬롯 ID로 지정 (--branch-id/--date/--time과 같이 못 씀)")
    parser.add_argument("--branch-id", type=int, default=None,
                         help="환전소(지점) ID. --date, --time과 함께 지정해야 함")
    parser.add_argument("--date", default=None,
                         help="픽업 날짜, YYYY-MM-DD (예: 2026-08-20)")
    parser.add_argument("--time", default=None,
                         help="픽업 시각, HH:MM (예: 14:30)")
    parser.add_argument("--apply", action="store_true", help="실제로 UPDATE 적용(기본은 dry-run)")
    args = parser.parse_args()

    branch_combo = [args.branch_id, args.date, args.time]
    branch_combo_given = any(v is not None for v in branch_combo)
    branch_combo_complete = all(v is not None for v in branch_combo)

    if args.slot_id is not None and branch_combo_given:
        parser.error("--slot-id와 --branch-id/--date/--time은 같이 쓸 수 없습니다.")
    if branch_combo_given and not branch_combo_complete:
        parser.error("--branch-id, --date, --time은 셋 다 같이 지정해야 합니다.")

    if not args.apply:
        log.warning("dry-run 모드 — 실제 변경 없음. 적용하려면 --apply를 추가하세요.")

    conn = connect()
    try:
        slot_id = args.slot_id
        if branch_combo_complete:
            slot_id = resolve_slot_id(conn, args.branch_id, args.date, args.time)
            if slot_id is None:
                log.error(
                    "branchId=%s date=%s time=%s에 해당하는 슬롯이 없습니다.",
                    args.branch_id, args.date, args.time,
                )
                return
            log.info(
                "branchId=%s date=%s time=%s -> slotId=%s",
                args.branch_id, args.date, args.time, slot_id,
            )

        violations = find_violations(conn, slot_id=slot_id)
        if not violations:
            log.info("위반 슬롯 없음")
            return

        log.info("위반 슬롯 %d건 발견", len(violations))
        fixed = 0
        for v in violations:
            if fix_one(conn, v["slot_id"], args.apply):
                fixed += 1

        if args.apply:
            log.info("완료: %d/%d건 수정", fixed, len(violations))
        else:
            log.info("dry-run 완료: %d/%d건이 수정 대상 (--apply로 재실행하면 적용)", fixed, len(violations))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
