package com.textbook.loop.dto;

/**
 * 课程关联的教材（含绑定类型与版本校验辅助字段）。
 */
public record CourseTextbookView(Long mappingId, Long courseId, Long textbookId, String title, String author,
                                 String publisher, String edition, String isbn13, java.math.BigDecimal price,
                                 String coverUrl, String bindType, String teacher, String semester, String source) {
}
