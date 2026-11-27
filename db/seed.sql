-- =============================================================================
-- 同窗书 · 演示数据（seed）
-- 用法：mysql -u root -p textbook_loop < db/seed.sql
-- 说明：密码不在此处写入，由后端 DataInitializer 在启动时用 BCrypt 生成：
--       管理员 admin / admin123
--       买家   buyer001 / 123456
--       卖家   seller001 / 123456
--       店员   clerk001 / 123456
-- =============================================================================
USE textbook_loop;

INSERT INTO sys_school (id, name, city, province, student_num) VALUES
(1, '示例大学', '杭州', '浙江', 20000);

INSERT INTO sys_user (id, nickname, phone, real_name, student_no, school_id, college, major, grade, dorm, credit_score, role, verify_state) VALUES
(1, '管理员', '13800000000', '系统管理员', '', 1, '', '', '', '', 100, 'ADMIN', 'VERIFIED'),
(2, '小黄同学', '13800000001', '黄一健', '2023010101', 1, '计算机学院', '软件工程', '2023', '3号楼302', 102, 'STUDENT', 'VERIFIED'),
(3, '学长清仓', '13800000002', '李墨', '2020010202', 1, '计算机学院', '软件工程', '2020', '7号楼101', 108, 'STUDENT', 'VERIFIED'),
(4, '三食堂店员', '13800000003', '王阿姨', '', 1, '', '', '', '', 100, 'CLERK', 'VERIFIED'),
(5, '班委小张', '13800000004', '张彤', '2023010303', 1, '计算机学院', '软件工程', '2023', '5号楼201', 105, 'LEADER', 'VERIFIED');

INSERT INTO sys_course (id, school_id, code, name, college, major, term, teacher, credit) VALUES
(1, 1, 'MATH1001', '高等数学 A（上）', '数学学院', '通用', '2026-2027-1', '陈老师', 5.0),
(2, 1, 'MATH1002', '线性代数', '数学学院', '通用', '2026-2027-1', '刘老师', 3.0),
(3, 1, 'ENG1001', '大学英语 综合教程 2', '外国语学院', '通用', '2026-2027-1', '赵老师', 4.0),
(4, 1, 'SE2001', '数据结构与算法', '计算机学院', '软件工程', '2026-2027-1', '周老师', 4.0),
(5, 1, 'SE2002', '数据库系统原理', '计算机学院', '软件工程', '2026-2027-1', '吴老师', 3.5),
(6, 1, 'SE2003', '计算机网络', '计算机学院', '软件工程', '2026-2027-1', '郑老师', 3.0);

INSERT INTO sys_textbook (id, isbn13, title, author, publisher, edition, publish_year, price, category) VALUES
(1, '9787040396638', '高等数学（第七版）上册', '同济大学数学系', '高等教育出版社', '第七版', 2014, 45.00, '公共课'),
(2, '9787040396645', '高等数学（第七版）下册', '同济大学数学系', '高等教育出版社', '第七版', 2014, 42.00, '公共课'),
(3, '9787040396607', '线性代数（第五版）', '同济大学数学系', '高等教育出版社', '第五版', 2007, 32.00, '公共课'),
(4, '9787040526998', '大学英语 综合教程 2（第3版）', '李荫华', '上海外语教育出版社', '第三版', 2019, 49.00, '公共课'),
(5, '9787115547385', '数据结构（C语言版）', '严蔚敏', '人民邮电出版社', '第二版', 2021, 59.00, '专业核心'),
(6, '9787040406542', '数据库系统概论（第5版）', '王珊', '高等教育出版社', '第五版', 2014, 42.00, '专业核心'),
(7, '9787115412829', '计算机网络（第7版）', '谢希仁', '电子工业出版社', '第七版', 2017, 49.00, '专业核心'),
(8, '9787040396652', '高等数学（第六版）上册', '同济大学数学系', '高等教育出版社', '第六版', 2007, 38.00, '公共课');

INSERT INTO sys_course_textbook (course_id, textbook_id, bind_type, teacher, semester, source) VALUES
(1, 1, 'REQUIRED', '陈老师', '2026-2027-1', 'MANUAL'),
(1, 8, 'REFERENCE', '陈老师', '2026-2027-1', 'MANUAL'),
(2, 3, 'REQUIRED', '刘老师', '2026-2027-1', 'MANUAL'),
(3, 4, 'REQUIRED', '赵老师', '2026-2027-1', 'MANUAL'),
(4, 5, 'REQUIRED', '周老师', '2026-2027-1', 'MANUAL'),
(5, 6, 'REQUIRED', '吴老师', '2026-2027-1', 'MANUAL'),
(6, 7, 'REQUIRED', '郑老师', '2026-2027-1', 'MANUAL');

INSERT INTO biz_pickup_point (id, school_id, name, address, longitude, latitude, open_time, close_time, capacity, manager_id) VALUES
(1, 1, '图书馆西门自提点', '图书馆西门一层', 120.123456, 30.274084, '12:00', '21:00', 300, 4),
(2, 1, '三食堂东门自提点', '三食堂东门快递柜旁', 120.125678, 30.275112, '11:00', '20:30', 200, 4);

INSERT INTO biz_listing (id, seller_id, textbook_id, course_id, title, price, original_price, condition_level, description, dorm, status, view_count, favorite_count, published_at) VALUES
(1, 3, 1, 1, '高等数学（第七版）上册', 15.00, 45.00, 'LIKE_NEW', '9成新，只在期末翻过，无笔记无划线', '7号楼101', 'ON_SALE', 46, 5, '2026-11-10 09:00:00'),
(2, 3, 3, 2, '线性代数（第五版）', 10.00, 32.00, 'GOOD', '8成新，前两章有铅笔笔记，可擦', '7号楼101', 'ON_SALE', 31, 3, '2026-11-10 09:05:00'),
(3, 3, 4, 3, '大学英语 综合教程 2（第3版）', 12.00, 49.00, 'LIKE_NEW', '含听力二维码未刮开', '7号楼101', 'ON_SALE', 22, 2, '2026-11-11 10:00:00'),
(4, 5, 5, 4, '数据结构（C语言版）', 20.00, 59.00, 'GOOD', '有课堂笔记，字迹工整', '5号楼201', 'ON_SALE', 18, 1, '2026-11-12 14:00:00'),
(5, 5, 6, 5, '数据库系统概论（第5版）', 14.00, 42.00, 'FAIR', '有较多笔记与荧光笔标记', '5号楼201', 'ON_SALE', 12, 0, '2026-11-13 16:00:00'),
(6, 2, 1, 1, '高等数学（第七版）上册', 16.00, 45.00, 'LIKE_NEW', '只翻过前 30 页', '3号楼302', 'ON_SALE', 9, 0, '2026-11-14 08:30:00');

INSERT INTO biz_listing_image (listing_id, url, md5, sort_no) VALUES
(1, '/static/demo/math7a.jpg', 'd41d8cd98f00b204e9800998ecf8427e', 0),
(2, '/static/demo/linalg5.jpg', '0cc175b9c0f1b6a831c399e269772661', 0),
(3, '/static/demo/eng2.jpg', '92eb5ffee6ae2fec3ad71c777531578f', 0),
(4, '/static/demo/ds.jpg', '4a8a08f09d37b73795649038408b5f33', 0),
(5, '/static/demo/db.jpg', '8277e0910d750195b448797616e091ad', 0),
(6, '/static/demo/math7a2.jpg', 'e358efa489f58062f10dd7316b65649e', 0);

INSERT INTO biz_group_buy (id, course_id, textbook_id, leader_id, title, target_count, unit_price, invite_code, current_count, deadline, status, commission_rate) VALUES
(1, 1, 1, 5, '高数A（上）第七版 · 三班教材接龙', 40, 15.00, 'MATH7A', 6, '2026-12-15 23:59:59', 'OPEN', 0.10);

INSERT INTO biz_group_buy_item (group_buy_id, user_id, quantity, contact, status) VALUES
(1, 5, 1, '微信 zhangtong', 'JOINED'),
(1, 2, 2, '微信 huangyj', 'JOINED');

INSERT INTO stat_daily (stat_date, school_id, gmv, order_count, verify_count, listing_count, new_user_count, refund_count) VALUES
('2026-11-10', 1, 15.00, 1, 1, 2, 1, 0),
('2026-11-11', 1, 22.00, 1, 1, 1, 2, 0),
('2026-11-12', 1, 20.00, 1, 0, 1, 1, 0),
('2026-11-13', 1, 14.00, 1, 1, 1, 3, 1),
('2026-11-14', 1, 46.00, 2, 2, 1, 2, 0);
