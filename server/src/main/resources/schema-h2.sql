-- =============================================================================
-- 同窗书 · H2 开发库 DDL（由 tools/gen-h2-schema.js 从 db/schema.sql 自动生成，请勿手改）
-- 用途 : spring.sql.init 首次启动建表（MODE=MySQL, DATABASE_TO_LOWER=TRUE）
-- =============================================================================

-- =============================================================================
-- 同窗书 · 校园教材循环平台  数据库设计
-- DBMS : MySQL 8.0+   引擎 : InnoDB   字符集 : utf8mb4
-- 说明 : 金额统一 DECIMAL(10,2)；状态统一 VARCHAR 枚举，便于状态机与可读性；
--        除流水表外统一带 deleted 逻辑删除位，配合 MyBatis-Plus。
-- 版本 : v1.0   （与 docs/02-数据库设计.md 一一对应）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 学校（单校起步，多校预留）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_school (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(64)  NOT NULL,
    city         VARCHAR(32)  NOT NULL DEFAULT '',
    province     VARCHAR(32)  NOT NULL DEFAULT '',
    student_num  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 2. 用户（学生 / 团长 / 自提点店员 / 管理员 四角色共表）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    openid       VARCHAR(64)  NULL,
    nickname     VARCHAR(64)  NOT NULL DEFAULT '',
    avatar       VARCHAR(255) NOT NULL DEFAULT '',
    phone        VARCHAR(20)  NOT NULL DEFAULT '',
    real_name    VARCHAR(32)  NOT NULL DEFAULT '',
    student_no   VARCHAR(32)  NOT NULL DEFAULT '',
    school_id    BIGINT       NOT NULL DEFAULT 0,
    college      VARCHAR(64)  NOT NULL DEFAULT '',
    major        VARCHAR(64)  NOT NULL DEFAULT '',
    grade        VARCHAR(16)  NOT NULL DEFAULT '',
    dorm         VARCHAR(64)  NOT NULL DEFAULT '',
    id_no        VARCHAR(32)  NOT NULL DEFAULT '',
    id_verified  TINYINT      NOT NULL DEFAULT 0,
    face_verified TINYINT     NOT NULL DEFAULT 0,
    student_verified TINYINT  NOT NULL DEFAULT 0,
    id_card_front VARCHAR(255) NOT NULL DEFAULT '',
    id_card_back  VARCHAR(255) NOT NULL DEFAULT '',
    student_card_cover VARCHAR(255) NOT NULL DEFAULT '',
    student_card_info  VARCHAR(255) NOT NULL DEFAULT '',
    merchant_id  BIGINT       NOT NULL DEFAULT 0,
    credit_score INT          NOT NULL DEFAULT 100,
    role         VARCHAR(16)  NOT NULL DEFAULT 'STUDENT',
    promo_code   VARCHAR(16)  NULL,
    verify_state VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED',
    status       VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    last_login   DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT sys_user_uk_openid UNIQUE (openid),
    CONSTRAINT sys_user_uk_promo_code UNIQUE (promo_code)
);

-- -----------------------------------------------------------------------------
-- 3. 认证凭据（密码 / 微信 / 短信 多通道，独立表便于扩展）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_auth (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    auth_type    VARCHAR(16)  NOT NULL,
    identifier   VARCHAR(64)  NOT NULL,
    credential   VARCHAR(128) NOT NULL DEFAULT '',
    verified     TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT user_auth_uk_type_identifier UNIQUE (auth_type, identifier)
);

-- -----------------------------------------------------------------------------
-- 4. 课程（本校课程库，教材映射的锚点）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_course (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    school_id    BIGINT       NOT NULL,
    code         VARCHAR(32)  NOT NULL DEFAULT '',
    name         VARCHAR(64)  NOT NULL,
    college      VARCHAR(64)  NOT NULL DEFAULT '',
    major        VARCHAR(64)  NOT NULL DEFAULT '',
    term         VARCHAR(16)  NOT NULL DEFAULT '',
    teacher      VARCHAR(32)  NOT NULL DEFAULT '',
    credit       DECIMAL(3,1) NOT NULL DEFAULT 0.0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 5. 教材主数据（ISBN 维度，一版一条）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_textbook (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    isbn13       VARCHAR(13)  NOT NULL DEFAULT '',
    isbn10       VARCHAR(10)  NOT NULL DEFAULT '',
    title        VARCHAR(128) NOT NULL,
    subtitle     VARCHAR(128) NOT NULL DEFAULT '',
    author       VARCHAR(128) NOT NULL DEFAULT '',
    publisher    VARCHAR(64)  NOT NULL DEFAULT '',
    edition      VARCHAR(32)  NOT NULL DEFAULT '',
    publish_year SMALLINT     NOT NULL DEFAULT 0,
    price        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    cover_url    VARCHAR(255) NOT NULL DEFAULT '',
    category     VARCHAR(32)  NOT NULL DEFAULT '公共课',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT sys_textbook_uk_isbn13 UNIQUE (isbn13)
);

-- -----------------------------------------------------------------------------
-- 6. 课程—教材映射（核心：解决"老师指定的到底是哪一本"）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_course_textbook (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    course_id    BIGINT       NOT NULL,
    textbook_id  BIGINT       NOT NULL,
    bind_type    VARCHAR(16)  NOT NULL DEFAULT 'REQUIRED',
    teacher      VARCHAR(32)  NOT NULL DEFAULT '',
    semester     VARCHAR(16)  NOT NULL DEFAULT '',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT sys_course_textbook_uk_course_textbook UNIQUE (course_id, textbook_id)
);

-- -----------------------------------------------------------------------------
-- 7. 商品（二手书在售条目）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_listing (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT       NOT NULL,
    merchant_id    BIGINT       NOT NULL DEFAULT 0,
    textbook_id    BIGINT       NOT NULL,
    course_id      BIGINT       NOT NULL DEFAULT 0,
    title          VARCHAR(160) NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    condition_level VARCHAR(16) NOT NULL DEFAULT 'LIKE_NEW',
    description    VARCHAR(500) NOT NULL DEFAULT '',
    cover_url      VARCHAR(255) NOT NULL DEFAULT '',
    video_url      VARCHAR(255) NOT NULL DEFAULT '',
    image_count    INT          NOT NULL DEFAULT 0,
    dorm           VARCHAR(64)  NOT NULL DEFAULT '',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ON_SALE',
    view_count     INT          NOT NULL DEFAULT 0,
    favorite_count INT          NOT NULL DEFAULT 0,
    reject_reason  VARCHAR(255) NOT NULL DEFAULT '',
    published_at   DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 8. 商品图片（MD5 去重、防重复发布）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_listing_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    url        VARCHAR(255) NOT NULL DEFAULT '',
    md5        VARCHAR(32)  NOT NULL DEFAULT '',
    sort_no    INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 9. 收藏
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    listing_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_favorite_uk_user_listing UNIQUE (user_id, listing_id)
);

-- -----------------------------------------------------------------------------
-- 10. 订单（担保交易核心表，订单号外部唯一）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(32)  NOT NULL,
    buyer_id       BIGINT       NOT NULL,
    seller_id      BIGINT       NOT NULL,
    listing_id     BIGINT       NOT NULL,
    textbook_id    BIGINT       NOT NULL DEFAULT 0,
    point_id       BIGINT       NOT NULL,
    amount         DECIMAL(10,2) NOT NULL,
    commission     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING_PAY',
    close_type     VARCHAR(24)  NOT NULL DEFAULT '',
    verify_code    VARCHAR(16)  NOT NULL DEFAULT '',
    verify_expire_at DATETIME   NULL,
    meet_place     VARCHAR(128) NOT NULL DEFAULT '',
    meet_time      DATETIME     NULL,
    sign_photo_url VARCHAR(255) NOT NULL DEFAULT '',
    sign_at        DATETIME     NULL,
    merchant_id    BIGINT       NOT NULL DEFAULT 0,
    pay_channel    VARCHAR(16)  NOT NULL DEFAULT 'MOCK',
    trade_no       VARCHAR(64)  NOT NULL DEFAULT '',
    remark         VARCHAR(255) NOT NULL DEFAULT '',
    refund_reason  VARCHAR(255) NOT NULL DEFAULT '',
    expire_at      DATETIME     NOT NULL,
    paid_at        DATETIME     NULL,
    verified_at    DATETIME     NULL,
    settled_at     DATETIME     NULL,
    completed_at   DATETIME     NULL,
    closed_at      DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT biz_orders_uk_order_no UNIQUE (order_no)
);

-- -----------------------------------------------------------------------------
-- 11. 幂等/请求去重（分布式环境防重复提交与重复回调）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_idempotent (
    request_id VARCHAR(64) NOT NULL,
    scene      VARCHAR(32) NOT NULL DEFAULT '',
    result     VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, scene)
);

-- -----------------------------------------------------------------------------
-- 12. 自提点
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_pickup_point (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    school_id  BIGINT       NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    address    VARCHAR(128) NOT NULL DEFAULT '',
    longitude  DECIMAL(10,6) NOT NULL DEFAULT 0,
    latitude   DECIMAL(10,6) NOT NULL DEFAULT 0,
    open_time  VARCHAR(16)  NOT NULL DEFAULT '12:00',
    close_time VARCHAR(16)  NOT NULL DEFAULT '21:00',
    capacity   INT          NOT NULL DEFAULT 200,
    manager_id BIGINT       NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 13. 核销记录（线下交付唯一凭证的落库）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_pickup_verify (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    order_no    VARCHAR(32) NOT NULL DEFAULT '',
    point_id    BIGINT      NOT NULL,
    operator_id BIGINT      NOT NULL,
    code        VARCHAR(64) NOT NULL DEFAULT '',
    method      VARCHAR(16) NOT NULL DEFAULT 'SCAN',
    client_ip   VARCHAR(45) NOT NULL DEFAULT '',
    verified_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_pickup_verify_uk_order UNIQUE (order_id)
);

-- -----------------------------------------------------------------------------
-- 14. 结算流水（T+1 打款，平台不碰资金池）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_settlement (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_id   BIGINT       NOT NULL,
    order_no   VARCHAR(32)  NOT NULL DEFAULT '',
    seller_id  BIGINT       NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    commission DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    batch_no   VARCHAR(32)  NOT NULL DEFAULT '',
    settle_at  DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_settlement_uk_order UNIQUE (order_id)
);

-- -----------------------------------------------------------------------------
-- 15. 评价（买卖双向）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_review (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    target_id   BIGINT       NOT NULL,
    reviewer_role VARCHAR(8) NOT NULL DEFAULT 'BUYER',
    rating      TINYINT      NOT NULL DEFAULT 5,
    content     VARCHAR(500) NOT NULL DEFAULT '',
    images      VARCHAR(500) NOT NULL DEFAULT '',
    anonymous   TINYINT      NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'VISIBLE',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_review_uk_order_role UNIQUE (order_id, reviewer_role)
);

-- -----------------------------------------------------------------------------
-- 16. 信用分流水（每次变动可追溯）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_credit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    change_val  INT          NOT NULL,
    score_after INT          NOT NULL,
    reason      VARCHAR(64)  NOT NULL DEFAULT '',
    ref_type    VARCHAR(16)  NOT NULL DEFAULT '',
    ref_id      BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 17. 纠纷工单
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_ticket (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL DEFAULT 0,
    order_no     VARCHAR(32)  NOT NULL DEFAULT '',
    initiator_id BIGINT       NOT NULL,
    against_id   BIGINT       NOT NULL DEFAULT 0,
    type         VARCHAR(16)  NOT NULL DEFAULT 'REFUND',
    title        VARCHAR(128) NOT NULL DEFAULT '',
    description  VARCHAR(1000) NOT NULL DEFAULT '',
    images       VARCHAR(500) NOT NULL DEFAULT '',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    result       VARCHAR(500) NOT NULL DEFAULT '',
    handler_id   BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at    DATETIME     NULL,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 18. 工单往来消息
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_ticket_message (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id  BIGINT        NOT NULL,
    sender_id  BIGINT        NOT NULL,
    sender_role VARCHAR(16)  NOT NULL DEFAULT 'USER',
    content    VARCHAR(1000) NOT NULL DEFAULT '',
    images     VARCHAR(500)  NOT NULL DEFAULT '',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 19. 班级接龙（C2B 反向聚合，本项目特色模块）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_group_buy (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    course_id    BIGINT       NOT NULL DEFAULT 0,
    textbook_id  BIGINT       NOT NULL DEFAULT 0,
    leader_id    BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    target_count INT          NOT NULL DEFAULT 40,
    unit_price   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    invite_code  VARCHAR(16)  NOT NULL DEFAULT '',
    current_count INT         NOT NULL DEFAULT 0,
    deadline     DATETIME     NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    commission_rate DECIMAL(4,2) NOT NULL DEFAULT 0.10,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT biz_group_buy_uk_invite UNIQUE (invite_code)
);

CREATE TABLE IF NOT EXISTS biz_group_buy_item (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    group_buy_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    quantity     INT         NOT NULL DEFAULT 1,
    contact      VARCHAR(32) NOT NULL DEFAULT '',
    status       VARCHAR(16) NOT NULL DEFAULT 'JOINED',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_group_buy_item_uk_group_user UNIQUE (group_buy_id, user_id)
);

-- -----------------------------------------------------------------------------
-- 20. 站内消息 / 订阅消息模板
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM',
    title      VARCHAR(128) NOT NULL DEFAULT '',
    content    VARCHAR(500) NOT NULL DEFAULT '',
    ref_type   VARCHAR(16)  NOT NULL DEFAULT '',
    ref_id     BIGINT       NOT NULL DEFAULT 0,
    read_flag  TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 21. 埋点事件（漏斗分析的数据源）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_track_event (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL DEFAULT 0,
    session_id VARCHAR(64) NOT NULL DEFAULT '',
    event      VARCHAR(32) NOT NULL,
    ref_type   VARCHAR(16) NOT NULL DEFAULT '',
    ref_id     BIGINT      NOT NULL DEFAULT 0,
    page       VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 22. 每日统计（定时任务产出，看板直接读）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stat_daily (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    stat_date     DATE         NOT NULL,
    school_id     BIGINT       NOT NULL DEFAULT 0,
    gmv           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    order_count   INT          NOT NULL DEFAULT 0,
    verify_count  INT          NOT NULL DEFAULT 0,
    listing_count INT          NOT NULL DEFAULT 0,
    new_user_count INT         NOT NULL DEFAULT 0,
    refund_count  INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT stat_daily_uk_date_school UNIQUE (stat_date, school_id)
);

-- -----------------------------------------------------------------------------
-- 23. 后台操作日志
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_admin_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT       NOT NULL,
    action     VARCHAR(64)  NOT NULL,
    target     VARCHAR(64)  NOT NULL DEFAULT '',
    detail     VARCHAR(500) NOT NULL DEFAULT '',
    ip         VARCHAR(45)  NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 24. 短信验证码（登录 / 交易二次验证；生产接入阿里云 SMS，开发走 mock）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_sms_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    phone      VARCHAR(20) NOT NULL,
    code       VARCHAR(8)  NOT NULL,
    purpose    VARCHAR(16) NOT NULL DEFAULT 'LOGIN',
    expire_at  DATETIME    NOT NULL,
    used       TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 25. 认证审核记录（实名 / 学生 / 人脸，便于追溯三方调用结果）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_verify_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    verify_type VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    provider    VARCHAR(32)  NOT NULL DEFAULT 'MOCK',
    payload     VARCHAR(500) NOT NULL DEFAULT '',
    result      VARCHAR(500) NOT NULL DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 26. 商户（可供书籍量大的商家入驻，批量上下架）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_merchant (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    shop_name   VARCHAR(64)  NOT NULL,
    license_url VARCHAR(255) NOT NULL DEFAULT '',
    contact     VARCHAR(64)  NOT NULL DEFAULT '',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(255) NOT NULL DEFAULT '',
    approved_by BIGINT       NOT NULL DEFAULT 0,
    approved_at DATETIME     NULL,
    listing_count INT        NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 27. 商品图片（扫码录入后按规范上传：封面/封底/出版日期页/随机四页正文）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_listing_photo (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    photo_type VARCHAR(16)  NOT NULL,
    page_no    INT          NOT NULL DEFAULT 0,
    url        VARCHAR(255) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 28. 交易/退货的时间地点确认（任一方发起，另一方确认）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_meet (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    meet_type    VARCHAR(16)  NOT NULL DEFAULT 'TRADE',
    proposer_id  BIGINT       NOT NULL,
    place        VARCHAR(128) NOT NULL,
    meet_time    DATETIME     NOT NULL,
    remark       VARCHAR(255) NOT NULL DEFAULT '',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PROPOSED',
    confirmed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 29. 退款退货单（协商 → 平台介入）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_refund (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    order_id         BIGINT        NOT NULL,
    applicant_id     BIGINT        NOT NULL,
    refund_type      VARCHAR(16)   NOT NULL DEFAULT 'RETURN',
    reason           VARCHAR(255)  NOT NULL,
    evidence_url     VARCHAR(255)  NOT NULL DEFAULT '',
    amount           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    platform_verdict VARCHAR(16)   NOT NULL DEFAULT '',
    platform_remark  VARCHAR(255)  NOT NULL DEFAULT '',
    decided_by       BIGINT        NOT NULL DEFAULT 0,
    decided_at       DATETIME      NULL,
    refunded_at      DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 30. 爽约上报（未按约定出现且未沟通，每次扣 5 信誉分）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_no_show (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    target_id   BIGINT       NOT NULL,
    photo_url   VARCHAR(255) NOT NULL DEFAULT '',
    description VARCHAR(255) NOT NULL DEFAULT '',
    credit_delta INT         NOT NULL DEFAULT -5,
    status      VARCHAR(16)  NOT NULL DEFAULT 'VALID',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 31. 会话（买卖双方围绕商品私信；下单后锁定订单聊天）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_conversation (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id    BIGINT       NOT NULL,
    buyer_id      BIGINT       NOT NULL,
    seller_id     BIGINT       NOT NULL,
    order_id      BIGINT       NOT NULL DEFAULT 0,
    last_message  VARCHAR(255) NOT NULL DEFAULT '',
    last_at       DATETIME     NULL,
    buyer_unread  INT          NOT NULL DEFAULT 0,
    seller_unread INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_conversation_uk_listing_buyer UNIQUE (listing_id, buyer_id)
);

-- -----------------------------------------------------------------------------
-- 32. 消息（文本 / 时间地点确认卡片 / 退款卡片 / 签到卡片）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    msg_type        VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
    content         VARCHAR(1000) NOT NULL DEFAULT '',
    ref_id          BIGINT       NOT NULL DEFAULT 0,
    read_flag       TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 34. 进阶码（平台下发给商户/运营的升级凭证，一码一用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_upgrade_code (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(16)  NOT NULL,
    target_role VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'UNUSED',
    batch_no    VARCHAR(32)  NOT NULL DEFAULT '',
    issued_by   BIGINT       NOT NULL DEFAULT 0,
    used_by     BIGINT       NOT NULL DEFAULT 0,
    used_at     DATETIME     NULL,
    expire_at   DATETIME     NULL,
    remark      VARCHAR(255) NOT NULL DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT sys_upgrade_code_uk_code UNIQUE (code)
);

-- -----------------------------------------------------------------------------
-- 35. 推广关系（运营推广码绑定下线，一个用户只归属一个运营）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_promotion (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    promoter_id BIGINT       NOT NULL,
    invitee_id  BIGINT       NOT NULL,
    promo_code  VARCHAR(16)  NOT NULL,
    source      VARCHAR(16)  NOT NULL DEFAULT 'CODE',
    bound_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_promotion_uk_invitee UNIQUE (invitee_id)
);

-- -----------------------------------------------------------------------------
-- 36. 提成明细（下线订单核销完成后按比例计提，平台端可结算）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_commission (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    promoter_id  BIGINT        NOT NULL,
    order_id     BIGINT        NOT NULL,
    order_no     VARCHAR(32)   NOT NULL,
    order_amount DECIMAL(10,2) NOT NULL,
    rate         DECIMAL(6,4)  NOT NULL,
    amount       DECIMAL(10,2) NOT NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    settle_at    DATETIME      NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT biz_commission_uk_order UNIQUE (order_id)
);

-- -----------------------------------------------------------------------------
-- 37. 线下认证与核实（运营线下核验身份证/学生证/商户资质）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_offline_verify (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    verify_type    VARCHAR(16)   NOT NULL DEFAULT 'STUDENT',
    material_url   VARCHAR(1000) NOT NULL DEFAULT '',
    applicant_note VARCHAR(255)  NOT NULL DEFAULT '',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    operator_id    BIGINT        NOT NULL DEFAULT 0,
    remark         VARCHAR(255)  NOT NULL DEFAULT '',
    verified_at    DATETIME      NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 用户附加身份：一个人一个账号，团长 / 商户 / 运营都是同一账号上叠加的身份。
-- 主身份仍存 sys_user.role（登录时选择的那重），本表只记"额外获得"的身份，
-- 例如商户用进阶码升级运营后：sys_user.role = 'MERCHANT'，本表 + ('OPERATOR')。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE IF NOT EXISTS sys_user_role (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    role           VARCHAR(16)   NOT NULL,
    source         VARCHAR(32)   NOT NULL DEFAULT 'UPGRADE',
    granted_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT sys_user_role_uk_user_role UNIQUE (user_id, role)
);
