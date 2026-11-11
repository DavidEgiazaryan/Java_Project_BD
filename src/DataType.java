import java.util.Date;

public enum DataType {
    INTEGER(Integer.class),
    DOUBLE(Double.class),
    STRING(String.class),
    DATE(Date.class),
    BOOLEAN(Boolean.class);

    private final Class<?> javaType;

    DataType(Class<?> javaType) {
        this.javaType = javaType;
    }

    Object normalize(Object value, String columnName) {
        if (value == null) {
            return null;
        }
        if (this == DOUBLE && value instanceof Integer) {
            value = ((Integer) value).doubleValue();
        }
        if (!javaType.isInstance(value)) {
            throw new IllegalArgumentException("Для столбца " + columnName
                    + " ожидается тип " + this);
        }
        if (this == DOUBLE) {
            double number = (Double) value;
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Для столбца " + columnName
                        + " требуется конечное число");
            }
            return number == 0.0 ? 0.0 : number;
        }
        return value;
    }
}
