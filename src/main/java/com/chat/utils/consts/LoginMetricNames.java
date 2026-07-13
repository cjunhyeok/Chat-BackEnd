package com.chat.utils.consts;

public final class LoginMetricNames {

    public static final String LOGIN_PERMIT_REJECTED_TOTAL = "login_permit_rejected_total";
    public static final String LOGIN_PERMIT_IN_USE = "login_permit_in_use";
    public static final String LOGIN_PERMIT_HOLD_DURATION = "login_permit_hold_duration_seconds";

    public static final String BCRYPT_PERMIT_WAIT_DURATION = "bcrypt_permit_wait_duration_seconds";
    public static final String BCRYPT_PERMIT_REJECTED_TOTAL = "bcrypt_permit_rejected_total";

    public static final String LOGIN_DB_QUERY_DURATION = "login_db_query_duration_seconds";

    private LoginMetricNames() {}
}
