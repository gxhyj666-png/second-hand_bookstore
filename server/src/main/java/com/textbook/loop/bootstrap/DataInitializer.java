package com.textbook.loop.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动灌入演示数据（幂等：sys_user 非空即跳过）。
 * 之所以用 JdbcTemplate 而不是 Mapper：种子数据都是"一次性、强形态"的固定行，
 * SQL 列名与 db/schema.sql 一一对应，改 schema 时改动点集中在这里。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Long count = jdbc.queryForObject("select count(*) from sys_user", Long.class);
        if (count != null && count > 0) {
            return;
        }
        long schoolId = 1L;
        jdbc.update("insert into sys_school (name, city, province, student_num) values (?,?,?,?)",
                "南江理工大学", "南京", "江苏", 24000);

        long adminId = user("管理员", "13800000001", "ADMIN", "教务处", "计算机学院", "软件工程", "2023",
                "行政楼 203", 100, "VERIFIED", "admin123");
        long buyerId = user("张同学", "13800000002", "STUDENT", "2023123456", "计算机学院", "软件工程", "2023",
                "梅园 3 栋 402", 100, "VERIFIED", "123456");
        long sellerId = user("李同学", "13800000003", "STUDENT", "2023123987", "数学学院", "统计学", "2022",
                "竹园 6 栋 211", 100, "VERIFIED", "123456");
        long clerkId = user("王书店", "13800000004", "CLERK", "", "后勤集团", "", "", "图书馆西门", 100, "VERIFIED", "123456");
        long leaderId = user("陈班长", "13800000005", "LEADER", "2023123111", "计算机学院", "软件工程", "2023",
                "梅园 3 栋 301", 100, "VERIFIED", "123456");

        long mathCourse = course("MATH101", "高等数学 A（上）", "数学学院", "计算机学院", "2026-2027-1", "赵平", new BigDecimal("5.0"));
        long linearCourse = course("MATH102", "线性代数", "数学学院", "计算机学院", "2026-2027-1", "周敏", new BigDecimal("3.0"));
        long englishCourse = course("ENG101", "大学英语（一）", "外国语学院", "", "2026-2027-1", "Linda", new BigDecimal("4.0"));
        long dsCourse = course("CS201", "数据结构", "计算机学院", "软件工程", "2026-2027-1", "吴强", new BigDecimal("4.0"));
        long netCourse = course("CS202", "计算机网络", "计算机学院", "软件工程", "2026-2027-1", "孙立", new BigDecimal("3.0"));
        course("MKT301", "市场营销学", "商学院", "工商管理", "2026-2027-1", "何芳", new BigDecimal("3.0"));

        long mathBook = book("9787040396638", "高等数学（第七版）上册", "同济大学数学系", "高等教育出版社",
                "第七版", 2014, new BigDecimal("49.80"), "专业基础");
        long linearBook = book("9787040396645", "线性代数（第六版）", "同济大学数学系", "高等教育出版社",
                "第六版", 2014, new BigDecimal("28.00"), "专业基础");
        long englishBook = book("9787513531603", "新视野大学英语 读写教程 1", "郑树棠", "外语教学与研究出版社",
                "第四版", 2020, new BigDecimal("45.90"), "公共课");
        long dsBook = book("9787302584957", "数据结构（C 语言版）", "严蔚敏 吴伟民", "清华大学出版社",
                "第二版", 2012, new BigDecimal("39.00"), "专业核心");
        long netBook = book("9787111544937", "计算机网络：自顶向下方法", "James F. Kurose", "机械工业出版社",
                "第七版", 2018, new BigDecimal("89.00"), "专业核心");
        long englishGrammar = book("9787513531993", "大学英语语法教程", "李静", "外语教学与研究出版社",
                "第三版", 2019, new BigDecimal("32.00"), "公共课");
        long probBook = book("9787040506969", "概率论与数理统计", "盛骤", "高等教育出版社",
                "第五版", 2019, new BigDecimal("36.50"), "专业基础");
        long pythonBook = book("9787115428028", "Python 编程：从入门到实践", "Eric Matthes", "人民邮电出版社",
                "第三版", 2023, new BigDecimal("79.80"), "通识");

        bind(mathCourse, mathBook, "REQUIRED", "赵平");
        bind(mathCourse, probBook, "REFERENCE", "赵平");
        bind(linearCourse, linearBook, "REQUIRED", "周敏");
        bind(englishCourse, englishBook, "REQUIRED", "Linda");
        bind(englishCourse, englishGrammar, "REFERENCE", "Linda");
        bind(dsCourse, dsBook, "REQUIRED", "吴强");
        bind(dsCourse, pythonBook, "REFERENCE", "吴强");
        bind(netCourse, netBook, "REQUIRED", "孙立");

        long point1 = pickupPoint(schoolId, "图书馆西门自提点", "图书馆西门一楼快递区", 118.7781, 32.0432,
                "09:00", "21:00", 200, clerkId);
        pickupPoint(schoolId, "三食堂门口自提点", "三食堂正门右手边货架", 118.7802, 32.0448,
                "11:00", "20:00", 120, 0);

        listing(sellerId, mathBook, mathCourse, "高等数学（第七版）上册 同济 9成新 无笔记", new BigDecimal("18.00"),
                new BigDecimal("49.80"), "LIKE_NEW", "只翻过前两章，书角有一点点压痕，内页干净无笔记，可小刀。", "竹园 6 栋", 6);
        listing(sellerId, dsBook, dsCourse, "数据结构（C 语言版）严蔚敏 8成新", new BigDecimal("15.00"),
                new BigDecimal("39.00"), "GOOD", "重点章节有荧光笔标注，不影响阅读，附赠手写笔记复印件。", "竹园 6 栋", 3);
        listing(sellerId, netBook, netCourse, "计算机网络 自顶向下方法 第七版", new BigDecimal("35.00"),
                new BigDecimal("89.00"), "LIKE_NEW", "正版机械工业，无划线，配套习题册一起送。", "竹园 6 栋", 2);
        listing(buyerId, englishBook, englishCourse, "新视野大学英语 1 第四版 含听力码", new BigDecimal("16.00"),
                new BigDecimal("45.90"), "GOOD", "听力二维码未刮开，封面有点旧，内页干净。", "梅园 3 栋", 4);
        listing(leaderId, linearBook, linearCourse, "线性代数 第六版 同济 全新未拆", new BigDecimal("14.00"),
                new BigDecimal("28.00"), "NEW", "买重了，全新未拆封，包装塑封完好。", "梅园 3 栋", 1);
        listing(buyerId, probBook, mathCourse, "概率论与数理统计 第五版 有笔记", new BigDecimal("12.00"),
                new BigDecimal("36.50"), "FAIR", "全书有较密课堂笔记，适合直接拿来复习。", "梅园 3 栋", 5);

        jdbc.update("insert into biz_group_buy (course_id, textbook_id, leader_id, title, target_count, unit_price, "
                        + "invite_code, current_count, deadline, status, commission_rate) values (?,?,?,?,?,?,?,?,?,?,?)",
                mathCourse, mathBook, leaderId, "高数 A（上）第七版 教材接龙", 40, new BigDecimal("20.00"),
                "JY2026", 0, LocalDateTime.now().plusDays(10), "OPEN", new BigDecimal("0.02"));

        LocalDate today = LocalDate.now();
        int[] gmv = {0, 0, 36, 52, 71, 88, 45};
        int[] orders = {0, 1, 2, 3, 4, 5, 3};
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            jdbc.update("insert into stat_daily (stat_date, school_id, gmv, order_count, verify_count, listing_count, "
                            + "new_user_count, refund_count) values (?,?,?,?,?,?,?,?)",
                    d, schoolId, new BigDecimal(gmv[i]), orders[i], Math.max(0, orders[i] - 1), i + 2, i + 1, 0);
        }
        log.info("示例数据初始化完成：admin/13800000001 admin123；其余账号密码 123456；团长陈班长 13800000005，接龙码 JY2026");
    }

    private long user(String nickname, String phone, String role, String studentNo, String college, String major,
                      String grade, String dorm, int credit, String verifyState, String password) {
        jdbc.update("insert into sys_user (nickname, phone, real_name, student_no, school_id, college, major, grade, "
                        + "dorm, credit_score, role, verify_state, status, avatar) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                nickname, phone, nickname, studentNo, 1L, college, major, grade, dorm, credit, role, verifyState,
                "NORMAL", "");
        long userId = jdbc.queryForObject("select id from sys_user where phone = ?", Long.class, phone);
        jdbc.update("insert into user_auth (user_id, auth_type, identifier, credential, verified) values (?,?,?,?,?)",
                userId, "PASSWORD", phone, encoder.encode(password), 1);
        return userId;
    }

    private long course(String code, String name, String college, String major, String term, String teacher,
                        BigDecimal credit) {
        jdbc.update("insert into sys_course (school_id, code, name, college, major, term, teacher, credit, status) "
                + "values (?,?,?,?,?,?,?,?,?)", 1L, code, name, college, major, term, teacher, credit, "ENABLED");
        return jdbc.queryForObject("select id from sys_course where code = ?", Long.class, code);
    }

    private long book(String isbn13, String title, String author, String publisher, String edition, int year,
                      BigDecimal price, String category) {
        jdbc.update("insert into sys_textbook (isbn13, title, author, publisher, edition, publish_year, price, "
                        + "category, status) values (?,?,?,?,?,?,?,?,?)",
                isbn13, title, author, publisher, edition, year, price, category, "ENABLED");
        return jdbc.queryForObject("select id from sys_textbook where isbn13 = ?", Long.class, isbn13);
    }

    private void bind(long courseId, long textbookId, String type, String teacher) {
        jdbc.update("insert into sys_course_textbook (course_id, textbook_id, bind_type, teacher, semester, source) "
                + "values (?,?,?,?,?,?)", courseId, textbookId, type, teacher, "2026-2027-1", "MANUAL");
    }

    private long pickupPoint(long schoolId, String name, String address, double lng, double lat,
                             String open, String close, int capacity, long managerId) {
        jdbc.update("insert into biz_pickup_point (school_id, name, address, longitude, latitude, open_time, "
                        + "close_time, capacity, manager_id, status) values (?,?,?,?,?,?,?,?,?,?)",
                schoolId, name, address, new BigDecimal(lng), new BigDecimal(lat), open, close, capacity, managerId,
                "ENABLED");
        return jdbc.queryForObject("select id from biz_pickup_point where name = ?", Long.class, name);
    }

    private void listing(long sellerId, long textbookId, long courseId, String title, BigDecimal price,
                         BigDecimal original, String condition, String desc, String dorm, int daysAgo) {
        jdbc.update("insert into biz_listing (seller_id, textbook_id, course_id, title, price, original_price, "
                        + "condition_level, description, cover_url, image_count, dorm, status, view_count, "
                        + "favorite_count, published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                sellerId, textbookId, courseId, title, price, original, condition, desc, "", 1, dorm,
                "ON_SALE", 12, 2, LocalDateTime.now().minusDays(daysAgo));
        Long listingId = jdbc.queryForObject("select max(id) from biz_listing", Long.class);
        String[] images = {"", "", ""};
        for (int i = 0; i < images.length; i++) {
            jdbc.update("insert into biz_listing_image (listing_id, url, md5, sort_no) values (?,?,?,?)",
                    listingId, images[i], "md5-" + listingId + "-" + i, i);
        }
        jdbc.update("insert into biz_track_event (user_id, session_id, event, ref_type, ref_id, page) values (?,?,?,?,?,?)",
                sellerId, "seed", "PUBLISH", "LISTING", listingId, "/pages/publish/index");
    }
}
