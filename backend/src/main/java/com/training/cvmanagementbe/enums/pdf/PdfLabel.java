package com.training.cvmanagementbe.enums.pdf;

import lombok.RequiredArgsConstructor;

/*
 * Fixed wording printed on the PDF, in the three CV languages.
 * The file goes to customers, so it is written in the CV's language rather than the UI's English.
 */
@RequiredArgsConstructor
public enum PdfLabel {

    // Section titles; personal info has none - it is the header
    CAREER_OBJECTIVE("Mục tiêu nghề nghiệp", "Career objective", "キャリア目標"),
    SKILLS("Kỹ năng", "Skills", "スキル"),
    EXPERIENCE("Kinh nghiệm làm việc", "Work experience", "職務経歴"),
    EDUCATION("Học vấn", "Education", "学歴"),
    CERTIFICATIONS("Chứng chỉ", "Certifications", "資格・認定"),
    PROJECTS("Dự án tiêu biểu", "Projects", "主なプロジェクト"),
    LANGUAGES("Ngoại ngữ", "Languages", "語学"),
    ADDITIONAL_INFO("Thông tin bổ sung", "Additional information", "その他"),

    // Contact lines in the header
    EMAIL("Email", "Email", "メール"),
    PHONE("Điện thoại", "Phone", "電話番号"),
    DATE_OF_BIRTH("Ngày sinh", "Date of birth", "生年月日"),
    ADDRESS("Địa chỉ", "Address", "住所"),

    // Entry details
    EXPIRES_ON("Ngày hết hạn", "Expires on", "有効期限"),
    CREDENTIAL_ID("Mã chứng chỉ", "Credential ID", "認定番号"),
    TEAM_SIZE("Quy mô nhóm", "Team size", "チーム規模"),
    TECHNOLOGIES("Công nghệ", "Technologies", "使用技術"),

    // Open-ended range and team size value
    PRESENT("Hiện tại", "Present", "現在"),
    TEAM_SIZE_VALUE("%s người", "%s members", "%s名");

    private final String vi;
    private final String en;
    private final String ja;

    public String text(PdfLocale locale) {
        return switch (locale) {
            case VI -> vi;
            case EN -> en;
            case JA -> ja;
        };
    }

    // For labels that carry a %s placeholder
    public String format(PdfLocale locale, Object... args) {
        return String.format(text(locale), args);
    }
}
