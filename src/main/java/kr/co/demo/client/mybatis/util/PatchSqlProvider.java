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
     * @param patch
     * @return
     */
    public static String build(Patch<?> patch) {
        Class<?> type = patch.domainType();

        String table = resolveTable(type);
        Field idField = resolveIdField(type);

        List<String> sets = new ArrayList<>();

        for (PatchValue<?> pv : patch.values()) {
            Field field = resolveField(type, pv.fieldName());
            StorageColumn column = field.getAnnotation(StorageColumn.class);

            if (field.isAnnotationPresent(StorageId.class)) continue;

            boolean nullable = column == null || column.nullable();
            String columnName = resolveColumn(field);

            if (pv.isNull() && !nullable) {
                throw new IllegalStateException(
                        "컬럼 '" + columnName + "' 은 nullable=false 이므로 NULL 로 업데이트할 수 없습니다"
                );
            }

            if (pv.isNull()) {
                sets.add(columnName + " = NULL");
            } else {
                sets.add(columnName + " = #{values[" +
                        patch.values().indexOf(pv) + "].value}");
            }
        }

        if (sets.isEmpty()) {
            throw new IllegalStateException("PATCH 대상 필드 없음");
        }

        System.out.println(
                "UPDATE " + table +
                        " SET " + String.join(", ", sets) +
                        " WHERE " + idField.getName() + " = #{id}"
        );

        return "UPDATE " + table +
                " SET " + String.join(", ", sets) +
                " WHERE " + idField.getName() + " = #{id}";
    }

    // ----------------- resolve helpers -----------------

    private static String resolveTable(Class<?> type) {
        StorageTable table = type.getAnnotation(StorageTable.class);
        if (table == null) {
            throw new IllegalStateException("@StorageTable 없음: " + type.getName());
        }
        return table.value();
    }

    private static Field resolveIdField(Class<?> type) {
        for (Field f : type.getDeclaredFields()) {
            if (f.isAnnotationPresent(StorageId.class)) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new IllegalStateException("@StorageId 필드 없음");
    }

    private static Field resolveField(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("존재하지 않는 필드: " + name);
        }
    }

    private static String resolveColumn(Field field) {
        StorageColumn column = field.getAnnotation(StorageColumn.class);
        return column == null || column.value().equals("") ? camelToSnake(field.getName()) : column.value();
    }

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
