erDiagram
USER {
bigint id PK
varchar name
varchar email
varchar password
varchar google_id
enum role
varchar phone
datetime created_at
datetime updated_at
}

    BRANCH {
        bigint id PK
        varchar name
        varchar address
        varchar phone
        decimal latitude
        decimal longitude
        varchar pickup_location_detail
        int time_slot_capacity
        datetime created_at
        datetime updated_at
    }

    BRANCH_OPERATING_HOURS {
        bigint id PK
        bigint branch_id FK
        tinyint day_of_week
        time open_time
        time close_time
        boolean is_closed
        datetime created_at
        datetime updated_at
    }

    BRANCH_TIME_SLOT_CONFIG {
        bigint id PK
        bigint branch_id FK
        tinyint day_of_week
        time start_time
        time end_time
        int capacity_limit
        boolean is_active
        datetime created_at
        datetime updated_at
    }

    BRANCH_TIME_SLOT_OVERRIDE {
        bigint id PK
        bigint branch_id FK
        date target_date
        time start_time
        time end_time
        int capacity_limit
        boolean is_blocked
        datetime created_at
        datetime updated_at
    }

    BRANCH_TIME_SLOT {
        bigint id PK
        bigint branch_id FK
        date slot_date
        time start_time
        time end_time
        int capacity
        int remaining
        datetime created_at
        datetime updated_at
    }

    CURRENCY {
        bigint id PK
        varchar code
        varchar country
        decimal buy_rate
        decimal sell_rate
        datetime updated_at
    }

    BRANCH_CURRENCY_INVENTORY {
        bigint id PK
        bigint branch_id FK
        bigint currency_id FK
        decimal preferential_rate
        decimal reservation_only_stock
        datetime updated_at
    }

    RESERVATION {
        bigint id PK
        bigint user_id FK
        bigint currency_id FK
        bigint time_slot_id FK
        decimal amount
        enum status
        varchar qr_code
        decimal locked_rate
        datetime picked_up_at
        datetime created_at
        datetime updated_at
    }

    NOTIFICATION {
        bigint id PK
        bigint user_id FK
        enum type
        varchar message
        boolean is_read
        datetime created_at
    }

    RECOMMENDATION_SIGNAL {
        bigint id PK
        bigint currency_id FK
        enum signal_type
        int window_days
        decimal value
        datetime calculated_at
    }

    AI_RECOMMENDATION {
        bigint id PK
        bigint currency_id FK
        enum recommendation
        text rationale
        decimal confidence_score
        varchar model_version
        datetime generated_at
        datetime expires_at
    }

    AI_RECOMMENDATION_SIGNAL {
        bigint id PK
        bigint recommendation_id FK
        bigint signal_id FK
    }

    TOOL_CALL_LOG {
        bigint id PK
        bigint recommendation_id FK
        varchar tool_name
        json tool_input
        json tool_output
        datetime called_at
    }

    EXCHANGE_RATE_HISTORY {
        bigint id PK
        bigint currency_id FK
        decimal rate
        datetime recorded_at
    }

    BACKTEST_RESULT {
        bigint id PK
        bigint currency_id FK
        enum strategy_type
        date period_start
        date period_end
        int total_signals
        int correct_signals
        decimal accuracy_rate
        datetime created_at
    }

    NEWS_ARTICLE {
        bigint id PK
        varchar currency_code
        varchar title
        varchar source
        varchar url
        datetime published_at
        decimal sentiment_score
        datetime collected_at
    }

    MACRO_INDICATOR {
        bigint id PK
        varchar country_code
        varchar indicator_type
        decimal value
        date recorded_at
    }

    BRANCH ||--o{ BRANCH_OPERATING_HOURS : "has"
    BRANCH ||--o{ BRANCH_TIME_SLOT_CONFIG : "has"
    BRANCH ||--o{ BRANCH_TIME_SLOT_OVERRIDE : "has"
    BRANCH ||--o{ BRANCH_TIME_SLOT : "has"
    BRANCH ||--o{ BRANCH_CURRENCY_INVENTORY : "has"
    CURRENCY ||--o{ BRANCH_CURRENCY_INVENTORY : "has"
    BRANCH_TIME_SLOT ||--o{ RESERVATION : "holds"
    USER ||--o{ RESERVATION : "makes"
    CURRENCY ||--o{ RESERVATION : "is_for"
    USER ||--o{ NOTIFICATION : "receives"
    CURRENCY ||--o{ RECOMMENDATION_SIGNAL : "has"
    CURRENCY ||--o{ AI_RECOMMENDATION : "has"
    AI_RECOMMENDATION ||--o{ AI_RECOMMENDATION_SIGNAL : "has"
    RECOMMENDATION_SIGNAL ||--o{ AI_RECOMMENDATION_SIGNAL : "has"
    AI_RECOMMENDATION ||--o{ TOOL_CALL_LOG : "has"
    CURRENCY ||--o{ EXCHANGE_RATE_HISTORY : "has"
    CURRENCY ||--o{ BACKTEST_RESULT : "has"
