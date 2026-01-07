package kr.co.demo.client.mybatis.util;

import kr.co.demo.core.storage.annotation.StorageColumn;
import kr.co.demo.core.storage.annotation.StorageId;
import kr.co.demo.core.storage.annotation.StorageTable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Update 문 생성해주는 클래스
 *
 * - 값이 있는 컬럼만 Set 생성
 */
public class PatchSqlProvider {

    /**
     * UPDATE 쿼리 생성 메서드
     *
     * @param domain 쿼리의 주체가 되는 도메인 객체
     * @return
     */
    public static String build(Object domain) {
        Class<?> type = domain.getClass();

        String table = resolveTable(type);
        Field idField = resolveIdField(type);
        String idColumn = resolveColumn(idField);

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(table).append(" SET ");

        List<String> sets = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);

            if (isId(field)) continue;

            // 컬럼 값
            Object value;
            try {
                // 필드값을 가져와 value 에 넣어줌
                value = field.get(domain);
            } catch (IllegalAccessException e) {
                continue;
            }

            // 값이 있을 때만 set 문 생성
            if (value != null && !value.equals("")) {
                sets.add(resolveColumn(field) + " = #{" + field.getName() + "}");
            }
        }

        // set 문이 없으면 Exception 처리
        if (sets.isEmpty()) {
            throw new IllegalStateException("PATCH 대상 필드 없음");
        }

        sql.append(String.join(", ", sets));
        sql.append(" WHERE ")
                .append(idColumn)
                .append(" = #{")
                .append(resolveIdField(type).getName())
                .append("}");

        System.out.println(sql);

        return sql.toString();
    }

    private static String resolveTable(Class<?> type) {
        StorageTable table = type.getAnnotation(StorageTable.class);
        if (table == null) {
            throw new IllegalStateException("@StorageTable 없음: " + type.getName());
        }
        return table.value();
    }

    private static Field resolveIdField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(StorageId.class)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("@StorageId 없음: " + type.getName());
    }

    private static boolean isId(Field field) {
        return field.isAnnotationPresent(StorageId.class);
    }

    private static String toColumn(String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (char c : fieldName.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String resolveColumn(Field field) {
        StorageColumn column = field.getAnnotation(StorageColumn.class);
        if (column != null && !column.value().isBlank()) {
            return column.value();
        }
        return toColumn(field.getName());
    }
}
