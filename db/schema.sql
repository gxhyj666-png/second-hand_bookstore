-- =============================================================================
-- 同窗书 · 校园教材循环平台  数据库设计
-- DBMS : MySQL 8.0+   引擎 : InnoDB   字符集 : utf8mb4
-- 说明 : 金额统一 DECIMAL(10,2)；状态统一 VARCHAR 枚举，便于状态机与可读性；
--        除流水表外统一带 deleted 逻辑删除位，配合 MyBatis-Plus。
-- 版本 : v1.0   （与 docs/02-数据库设计.md 一一对应）
-- =============================================================================

DROP DATABASE IF EXISTS textbook_loop;
CREATE DATABASE textbook_loop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE textbook_loop;

-- -----------------------------------------------------------------------------
-- 1. 学校（单校起步，多校预留）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_school (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(64)  NOT NULL COMMENT '学校名称',
    city         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '所在城市',
    province     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '所在省份',
    student_num  INT          NOT NULL DEFAULT 0 COMMENT '在校生规模（用于渗透率测算）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校';

-- -----------------------------------------------------------------------------
-- 2. 用户（学生 / 团长 / 自提点店员 / 管理员 四角色共表）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    openid       VARCHAR(64)  NULL COMMENT '微信 openid（未绑定微信时为 NULL，唯一索引允许多个 NULL）',
    nickname     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    avatar       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像 URL',
    phone        VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '手机号（加密存储可扩展）',
    real_name    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '真实姓名',
    student_no   VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '学号',
    school_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '学校 ID',
    college      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学院',
    major        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '专业',
    grade        VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '年级 如 2023',
    dorm         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '宿舍楼/房间，用于距离排序',
    id_no        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '身份证号（对外脱敏）',
    id_verified  TINYINT      NOT NULL DEFAULT 0 COMMENT '实名认证：0 否 1 是',
    face_verified TINYINT     NOT NULL DEFAULT 0 COMMENT '人脸核身：0 否 1 是',
    student_verified TINYINT  NOT NULL DEFAULT 0 COMMENT '学生认证：0 否 1 是',
    id_card_front VARCHAR(255) NOT NULL DEFAULT '' COMMENT '身份证人像面图片',
    id_card_back  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '身份证国徽面图片',
    student_card_cover VARCHAR(255) NOT NULL DEFAULT '' COMMENT '学生证封面图片',
    student_card_info  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '学生证信息页图片',
    merchant_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '所属商户 ID，0 表示个人卖家',
    credit_score INT          NOT NULL DEFAULT 100 COMMENT '信用分，默认 100',
    role         VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/LEADER/MERCHANT/ADMIN',
    verify_state VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/PENDING/VERIFIED/REJECTED',
    status       VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN',
    last_login   DATETIME     NULL COMMENT '最后登录时间',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid),
    KEY idx_phone (phone),
    KEY idx_student (school_id, student_no),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- -----------------------------------------------------------------------------
-- 3. 认证凭据（密码 / 微信 / 短信 多通道，独立表便于扩展）
-- -----------------------------------------------------------------------------
CREATE TABLE user_auth (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL COMMENT '用户 ID',
    auth_type    VARCHAR(16)  NOT NULL COMMENT 'PASSWORD/WECHAT/SMS',
    identifier   VARCHAR(64)  NOT NULL COMMENT '登录名/手机号/openid',
    credential   VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 密码密文或凭证',
    verified     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否已验证',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_identifier (auth_type, identifier),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证凭据';

-- -----------------------------------------------------------------------------
-- 4. 课程（本校课程库，教材映射的锚点）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_course (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    school_id    BIGINT       NOT NULL COMMENT '学校 ID',
    code         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '课程代码',
    name         VARCHAR(64)  NOT NULL COMMENT '课程名',
    college      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '开课学院',
    major        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '适用专业（空=通用）',
    term         VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '学期 如 2026-2027-1',
    teacher      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '授课教师',
    credit       DECIMAL(3,1) NOT NULL DEFAULT 0.0 COMMENT '学分',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_school_major (school_id, major),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程';

-- -----------------------------------------------------------------------------
-- 5. 教材主数据（ISBN 维度，一版一条）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_textbook (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    isbn13       VARCHAR(13)  NOT NULL DEFAULT '' COMMENT 'ISBN-13',
    isbn10       VARCHAR(10)  NOT NULL DEFAULT '' COMMENT 'ISBN-10',
    title        VARCHAR(128) NOT NULL COMMENT '书名',
    subtitle     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '副标题',
    author       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '作者',
    publisher    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '出版社',
    edition      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '版次 如 第七版',
    publish_year SMALLINT     NOT NULL DEFAULT 0 COMMENT '出版年',
    price        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '定价',
    cover_url    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面图',
    category     VARCHAR(32)  NOT NULL DEFAULT '公共课' COMMENT '公共课/专业基础/专业核心/通识',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_isbn13 (isbn13),
    KEY idx_title (title),
    KEY idx_publisher (publisher)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教材主数据';

-- -----------------------------------------------------------------------------
-- 6. 课程—教材映射（核心：解决"老师指定的到底是哪一本"）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_course_textbook (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    course_id    BIGINT       NOT NULL COMMENT '课程 ID',
    textbook_id  BIGINT       NOT NULL COMMENT '教材 ID',
    bind_type    VARCHAR(16)  NOT NULL DEFAULT 'REQUIRED' COMMENT 'REQUIRED指定/REFERENCE参考',
    teacher      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '授课教师（版本校验第三重）',
    semester     VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '适用学期',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL人工/OCR识别/学生补充',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_textbook (course_id, textbook_id),
    KEY idx_textbook (textbook_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-教材映射';

-- -----------------------------------------------------------------------------
-- 7. 商品（二手书在售条目）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_listing (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT       NOT NULL COMMENT '卖家',
    merchant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '所属商户 ID，0 表示个人卖家',
    textbook_id    BIGINT       NOT NULL COMMENT '教材 ID',
    course_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '关联课程（用于同课匹配）',
    title          VARCHAR(160) NOT NULL COMMENT '展示标题（书名+版次）',
    price          DECIMAL(10,2) NOT NULL COMMENT '售价',
    original_price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价（定价）',
    condition_level VARCHAR(16) NOT NULL DEFAULT 'LIKE_NEW' COMMENT 'NEW/ LIKE_NEW(9成新)/GOOD(8成新)/FAIR(有笔记)/POOR',
    description    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '描述',
    cover_url      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封面（首图）',
    image_count    INT          NOT NULL DEFAULT 0 COMMENT '图片数',
    dorm           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '所在宿舍楼',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/LOCKED/SOLD/OFFLINE/REJECTED',
    view_count     INT          NOT NULL DEFAULT 0,
    favorite_count INT          NOT NULL DEFAULT 0,
    reject_reason  VARCHAR(255) NOT NULL DEFAULT '',
    published_at   DATETIME     NULL COMMENT '上架时间（排序用）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_seller (seller_id, status),
    KEY idx_textbook_status (textbook_id, status),
    KEY idx_course_status (course_id, status),
    KEY idx_status_time (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手书商品';

-- -----------------------------------------------------------------------------
-- 8. 商品图片（MD5 去重、防重复发布）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_listing_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    url        VARCHAR(255) NOT NULL DEFAULT '',
    md5        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '文件 MD5，用于重复检测',
    sort_no    INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_listing (listing_id, sort_no),
    KEY idx_md5 (md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片';

-- -----------------------------------------------------------------------------
-- 9. 收藏
-- -----------------------------------------------------------------------------
CREATE TABLE biz_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    listing_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_listing (user_id, listing_id),
    KEY idx_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

-- -----------------------------------------------------------------------------
-- 10. 订单（担保交易核心表，订单号外部唯一）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(32)  NOT NULL COMMENT '订单号  TB+yyyyMMddHHmmss+6位随机',
    buyer_id       BIGINT       NOT NULL,
    seller_id      BIGINT       NOT NULL,
    listing_id     BIGINT       NOT NULL,
    textbook_id    BIGINT       NOT NULL DEFAULT 0,
    point_id       BIGINT       NOT NULL COMMENT '自提点',
    amount         DECIMAL(10,2) NOT NULL COMMENT '商品金额',
    commission     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台服务费',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/WAIT_VERIFY/COMPLETED/REFUNDING/REFUND_AGREED/REFUNDED/ARBITRATING/CLOSED（旧值 FROZEN/VERIFIED/SETTLED 兼容读取）',
    verify_code    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '6 位随机核销码，买家出示、卖家输入',
    verify_expire_at DATETIME   NULL COMMENT '核销码有效期（生成后 15 分钟）',
    meet_place     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '交易地点（双方确认）',
    meet_time      DATETIME     NULL COMMENT '交易时间（双方确认）',
    sign_photo_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '线下交易签到照片（卖家上传）',
    sign_at        DATETIME     NULL COMMENT '签到时间',
    merchant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '商户订单所属商户 ID，0 为个人',
    pay_channel    VARCHAR(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'WECHAT/MOCK',
    trade_no       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '第三方支付流水号',
    remark         VARCHAR(255) NOT NULL DEFAULT '',
    refund_reason  VARCHAR(255) NOT NULL DEFAULT '',
    expire_at      DATETIME     NOT NULL COMMENT '支付超时时间（15 分钟）',
    paid_at        DATETIME     NULL,
    verified_at    DATETIME     NULL,
    settled_at     DATETIME     NULL,
    completed_at   DATETIME     NULL COMMENT '交易完成时间（核销后）',
    closed_at      DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer (buyer_id, status),
    KEY idx_seller (seller_id, status),
    KEY idx_listing (listing_id),
    KEY idx_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- -----------------------------------------------------------------------------
-- 11. 幂等/请求去重（分布式环境防重复提交与重复回调）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_idempotent (
    request_id VARCHAR(64) NOT NULL COMMENT '客户端 requestId 或回调流水号',
    scene      VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'CREATE_ORDER/PAY_CALLBACK/VERIFY',
    result     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '首次处理结果摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等表';

-- -----------------------------------------------------------------------------
-- 12. 自提点
-- -----------------------------------------------------------------------------
CREATE TABLE biz_pickup_point (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    school_id  BIGINT       NOT NULL,
    name       VARCHAR(64)  NOT NULL COMMENT '如 图书馆西门',
    address    VARCHAR(128) NOT NULL DEFAULT '',
    longitude  DECIMAL(10,6) NOT NULL DEFAULT 0 COMMENT '经度',
    latitude   DECIMAL(10,6) NOT NULL DEFAULT 0 COMMENT '纬度',
    open_time  VARCHAR(16)  NOT NULL DEFAULT '12:00' COMMENT '营业开始',
    close_time VARCHAR(16)  NOT NULL DEFAULT '21:00' COMMENT '营业结束',
    capacity   INT          NOT NULL DEFAULT 200 COMMENT '货位容量',
    manager_id BIGINT       NOT NULL DEFAULT 0 COMMENT '店员用户 ID',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_school (school_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自提点';

-- -----------------------------------------------------------------------------
-- 13. 核销记录（线下交付唯一凭证的落库）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_pickup_verify (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    order_no    VARCHAR(32) NOT NULL DEFAULT '',
    point_id    BIGINT      NOT NULL,
    operator_id BIGINT      NOT NULL COMMENT '核销店员',
    code        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '核销码',
    method      VARCHAR(16) NOT NULL DEFAULT 'SCAN' COMMENT 'SCAN/MANUAL',
    client_ip   VARCHAR(45) NOT NULL DEFAULT '',
    verified_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order (order_id),
    KEY idx_point_time (point_id, verified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核销记录';

-- -----------------------------------------------------------------------------
-- 14. 结算流水（T+1 打款，平台不碰资金池）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_settlement (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_id   BIGINT       NOT NULL,
    order_no   VARCHAR(32)  NOT NULL DEFAULT '',
    seller_id  BIGINT       NOT NULL,
    amount     DECIMAL(10,2) NOT NULL COMMENT '结算给卖家金额 = 订单金额 - 服务费',
    commission DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台服务费',
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
    batch_no   VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '结算批次',
    settle_at  DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order (order_id),
    KEY idx_seller_status (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算流水';

-- -----------------------------------------------------------------------------
-- 15. 评价（买卖双向）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_review (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    target_id   BIGINT       NOT NULL COMMENT '被评价人',
    reviewer_role VARCHAR(8) NOT NULL DEFAULT 'BUYER' COMMENT 'BUYER/SELLER',
    rating      TINYINT      NOT NULL DEFAULT 5 COMMENT '1-5 星',
    content     VARCHAR(500) NOT NULL DEFAULT '',
    images      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '逗号分隔',
    anonymous   TINYINT      NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE/HIDDEN',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_role (order_id, reviewer_role),
    KEY idx_target (target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';

-- -----------------------------------------------------------------------------
-- 16. 信用分流水（每次变动可追溯）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_credit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    change_val  INT          NOT NULL COMMENT '变动值，可为负',
    score_after INT          NOT NULL COMMENT '变动后分数',
    reason      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'DEAL_DONE/REVIEW_GOOD/TIMEOUT/FAKE_BOOK/DISPUTE_LOST',
    ref_type    VARCHAR(16)  NOT NULL DEFAULT '',
    ref_id      BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分流水';

-- -----------------------------------------------------------------------------
-- 17. 纠纷工单
-- -----------------------------------------------------------------------------
CREATE TABLE biz_ticket (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL DEFAULT 0,
    order_no     VARCHAR(32)  NOT NULL DEFAULT '',
    initiator_id BIGINT       NOT NULL COMMENT '发起人',
    against_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '被投诉人',
    type         VARCHAR(16)  NOT NULL DEFAULT 'REFUND' COMMENT 'REFUND/FAKE_BOOK/CONDITION/OTHER',
    title        VARCHAR(128) NOT NULL DEFAULT '',
    description  VARCHAR(1000) NOT NULL DEFAULT '',
    images       VARCHAR(500) NOT NULL DEFAULT '',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/REJECTED',
    result       VARCHAR(500) NOT NULL DEFAULT '',
    handler_id   BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at    DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纠纷工单';

-- -----------------------------------------------------------------------------
-- 18. 工单往来消息
-- -----------------------------------------------------------------------------
CREATE TABLE biz_ticket_message (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id  BIGINT        NOT NULL,
    sender_id  BIGINT        NOT NULL,
    sender_role VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
    content    VARCHAR(1000) NOT NULL DEFAULT '',
    images     VARCHAR(500)  NOT NULL DEFAULT '',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单消息';

-- -----------------------------------------------------------------------------
-- 19. 班级接龙（C2B 反向聚合，本项目特色模块）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_group_buy (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    course_id    BIGINT       NOT NULL DEFAULT 0,
    textbook_id  BIGINT       NOT NULL DEFAULT 0,
    leader_id    BIGINT       NOT NULL COMMENT '团长（班委）',
    title        VARCHAR(128) NOT NULL COMMENT '如 高数A 第七版 教材接龙',
    target_count INT          NOT NULL DEFAULT 40 COMMENT '目标册数',
    unit_price   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预期单价',
    invite_code  VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '邀请码/短码',
    current_count INT         NOT NULL DEFAULT 0 COMMENT '已登记数量',
    deadline     DATETIME     NOT NULL COMMENT '截止时间',
    status       VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED/DONE/CANCELED',
    commission_rate DECIMAL(4,2) NOT NULL DEFAULT 0.10 COMMENT '团长佣金比例',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite (invite_code),
    KEY idx_leader (leader_id, status),
    KEY idx_status (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级接龙（团购）';

CREATE TABLE biz_group_buy_item (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    group_buy_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    quantity     INT         NOT NULL DEFAULT 1 COMMENT '需求册数',
    contact      VARCHAR(32) NOT NULL DEFAULT '' COMMENT '联系方式',
    status       VARCHAR(16) NOT NULL DEFAULT 'JOINED' COMMENT 'JOINED/MATCHED/CANCELED',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_buy_id, user_id),
    KEY idx_group (group_buy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接龙登记';

-- -----------------------------------------------------------------------------
-- 20. 站内消息 / 订阅消息模板
-- -----------------------------------------------------------------------------
CREATE TABLE msg_notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/ORDER/CREDIT/TICKET',
    title      VARCHAR(128) NOT NULL DEFAULT '',
    content    VARCHAR(500) NOT NULL DEFAULT '',
    ref_type   VARCHAR(16)  NOT NULL DEFAULT '',
    ref_id     BIGINT       NOT NULL DEFAULT 0,
    read_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, read_flag, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- -----------------------------------------------------------------------------
-- 21. 埋点事件（漏斗分析的数据源）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_track_event (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL DEFAULT 0,
    session_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '一次访问会话',
    event      VARCHAR(32) NOT NULL COMMENT 'VIEW_LISTING/DETAIL/PUBLISH/CREATE_ORDER/PAY/VERIFY',
    ref_type   VARCHAR(16) NOT NULL DEFAULT '',
    ref_id     BIGINT      NOT NULL DEFAULT 0,
    page       VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_time (event, created_at),
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='埋点事件';

-- -----------------------------------------------------------------------------
-- 22. 每日统计（定时任务产出，看板直接读）
-- -----------------------------------------------------------------------------
CREATE TABLE stat_daily (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    stat_date     DATE         NOT NULL,
    school_id     BIGINT       NOT NULL DEFAULT 0,
    gmv           DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '成交额',
    order_count   INT          NOT NULL DEFAULT 0 COMMENT '订单数',
    verify_count  INT          NOT NULL DEFAULT 0 COMMENT '核销数',
    listing_count INT          NOT NULL DEFAULT 0 COMMENT '新上架数',
    new_user_count INT         NOT NULL DEFAULT 0,
    refund_count  INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_school (stat_date, school_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计';

-- -----------------------------------------------------------------------------
-- 23. 后台操作日志
-- -----------------------------------------------------------------------------
CREATE TABLE sys_admin_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT       NOT NULL,
    action     VARCHAR(64)  NOT NULL COMMENT 'LISTING_REJECT/USER_FREEZE/TICKET_RESOLVE',
    target     VARCHAR(64)  NOT NULL DEFAULT '',
    detail     VARCHAR(500) NOT NULL DEFAULT '',
    ip         VARCHAR(45)  NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_time (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志';

-- -----------------------------------------------------------------------------
-- 24. 短信验证码（登录 / 交易二次验证；生产接入阿里云 SMS，开发走 mock）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_sms_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    phone      VARCHAR(20) NOT NULL,
    code       VARCHAR(8)  NOT NULL,
    purpose    VARCHAR(16) NOT NULL DEFAULT 'LOGIN' COMMENT 'LOGIN/TRADE',
    expire_at  DATETIME    NOT NULL,
    used       TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_phone_purpose (phone, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

-- -----------------------------------------------------------------------------
-- 25. 认证审核记录（实名 / 学生 / 人脸，便于追溯三方调用结果）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_verify_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    verify_type VARCHAR(16)  NOT NULL COMMENT 'ID_CARD/STUDENT/FACE',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PASS/REJECT',
    provider    VARCHAR(32)  NOT NULL DEFAULT 'MOCK' COMMENT 'ALIYUN/MOCK',
    payload     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '请求摘要（脱敏）',
    result      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '三方返回摘要',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_type (user_id, verify_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证审核记录';

-- -----------------------------------------------------------------------------
-- 26. 商户（可供书籍量大的商家入驻，批量上下架）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_merchant (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '商户主账号',
    shop_name   VARCHAR(64)  NOT NULL,
    license_url VARCHAR(255) NOT NULL DEFAULT '' COMMENT '营业执照/校园代理授权',
    contact     VARCHAR(64)  NOT NULL DEFAULT '',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/FROZEN',
    reject_reason VARCHAR(255) NOT NULL DEFAULT '',
    approved_by BIGINT       NOT NULL DEFAULT 0,
    approved_at DATETIME     NULL,
    listing_count INT        NOT NULL DEFAULT 0 COMMENT '在架数量（冗余，批量操作后刷新）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户';

-- -----------------------------------------------------------------------------
-- 27. 商品图片（扫码录入后按规范上传：封面/封底/出版日期页/随机四页正文）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_listing_photo (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    photo_type VARCHAR(16)  NOT NULL COMMENT 'COVER/BACK/PUBDATE/PAGE',
    page_no    INT          NOT NULL DEFAULT 0 COMMENT '正文页码（PAGE 类型）',
    url        VARCHAR(255) NOT NULL,
    sort_no    INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_listing (listing_id, photo_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片';

-- -----------------------------------------------------------------------------
-- 28. 交易/退货的时间地点确认（任一方发起，另一方确认）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_meet (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    meet_type    VARCHAR(16)  NOT NULL DEFAULT 'TRADE' COMMENT 'TRADE/RETURN',
    proposer_id  BIGINT       NOT NULL,
    place        VARCHAR(128) NOT NULL,
    meet_time    DATETIME     NOT NULL,
    remark       VARCHAR(255) NOT NULL DEFAULT '',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PROPOSED' COMMENT 'PROPOSED/CONFIRMED/CANCELED',
    confirmed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id, meet_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易时间地点确认';

-- -----------------------------------------------------------------------------
-- 29. 退款退货单（协商 → 平台介入）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_refund (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    order_id         BIGINT        NOT NULL,
    applicant_id     BIGINT        NOT NULL COMMENT '申请方（通常买家）',
    reason           VARCHAR(255)  NOT NULL,
    evidence_url     VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '凭证照片',
    amount           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/AGREED/REJECTED/ARBITRATING/ARBITRATED',
    platform_verdict VARCHAR(16)   NOT NULL DEFAULT '' COMMENT 'PLATFORM_OK（货物无问题）/SELLER_FAULT',
    platform_remark  VARCHAR(255)  NOT NULL DEFAULT '',
    decided_by       BIGINT        NOT NULL DEFAULT 0,
    decided_at       DATETIME      NULL,
    refunded_at      DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款退货单';

-- -----------------------------------------------------------------------------
-- 30. 爽约上报（未按约定出现且未沟通，每次扣 5 信誉分）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_no_show (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    target_id   BIGINT       NOT NULL,
    photo_url   VARCHAR(255) NOT NULL DEFAULT '',
    description VARCHAR(255) NOT NULL DEFAULT '',
    credit_delta INT         NOT NULL DEFAULT -5,
    status      VARCHAR(16)  NOT NULL DEFAULT 'VALID' COMMENT 'VALID/REVOKED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爽约上报';

-- -----------------------------------------------------------------------------
-- 31. 会话（买卖双方围绕商品私信；下单后锁定订单聊天）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_conversation (
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
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_listing_buyer (listing_id, buyer_id),
    KEY idx_buyer (buyer_id, last_at),
    KEY idx_seller (seller_id, last_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

-- -----------------------------------------------------------------------------
-- 32. 消息（文本 / 时间地点确认卡片 / 退款卡片 / 签到卡片）
-- -----------------------------------------------------------------------------
CREATE TABLE biz_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    msg_type        VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/MEET_CARD/REFUND_CARD/SIGN_CARD',
    content         VARCHAR(1000) NOT NULL DEFAULT '',
    ref_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '关联的 meet/refund 主键',
    read_flag       TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';
