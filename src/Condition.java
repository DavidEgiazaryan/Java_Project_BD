import java.util.Date;

public final class Condition {
    public enum Operator {
        EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL
    }

    private final String column;
    private final Operator operator;
    private final Object value;

    public Condition(String column, Operator operator, Object value) {
        if (column == null || column.trim().isEmpty() || operator == null) {
            throw new IllegalArgumentException("Для условия нужны столбец и оператор");
        }
        this.column = column;
        this.operator = operator;
        this.value = Row.copyValue(value);
    }

    public String getColumn() { return column; }

    Object normalizeValue(Column definition) {
        if (definition.getType() == DataType.BOOLEAN
                && operator != Operator.EQUAL && operator != Operator.NOT_EQUAL) {
            throw new IllegalArgumentException("Для BOOLEAN доступны только = и !=");
        }
        return definition.getType().normalize(value, definition.getName());
    }

    boolean matches(Object actual, Object expected, DataType type) {
        if (actual == null || expected == null) {
            return false;
        }
        int comparison = compare(actual, expected, type);
        switch (operator) {
            case EQUAL: return comparison == 0;
            case NOT_EQUAL: return comparison != 0;
            case LESS: return comparison < 0;
            case GREATER: return comparison > 0;
            case LESS_OR_EQUAL: return comparison <= 0;
            case GREATER_OR_EQUAL: return comparison >= 0;
            default: throw new AssertionError(operator);
        }
    }

    private int compare(Object actual, Object expected, DataType type) {
        switch (type) {
            case INTEGER: return ((Integer) actual).compareTo((Integer) expected);
            case DOUBLE: return ((Double) actual).compareTo((Double) expected);
            case STRING: return ((String) actual).compareTo((String) expected);
            case BOOLEAN: return ((Boolean) actual).compareTo((Boolean) expected);
            case DATE:
                long actualDay = Math.floorDiv(((Date) actual).getTime(), 86400000L);
                long expectedDay = Math.floorDiv(((Date) expected).getTime(), 86400000L);
                return Long.compare(actualDay, expectedDay);
            default: throw new AssertionError(type);
        }
    }
}
