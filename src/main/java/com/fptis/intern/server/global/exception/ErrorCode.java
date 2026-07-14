package com.fptis.intern.server.global.exception;

public interface ErrorCode {
    int getStatus();
    String getCode();
    String getMessage();
}