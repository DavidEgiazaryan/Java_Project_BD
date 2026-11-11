import java.io.Serializable;

public final class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean required;
    private final boolean primaryKey;
    private final String referencedTable;

    public Column(String name, DataType type, boolean required) {
        this(name, type, required, false, null);
    }

    private Column(String name, DataType type, boolean required, boolean primaryKey,
                   String referencedTable) {
        if (name == null || name.trim().isEmpty() || type == null) {
            throw new IllegalArgumentException("Имя и тип столбца обязательны");
        }
        this.name = name;
        this.type = type;
        this.required = required;
        this.primaryKey = primaryKey;
        this.referencedTable = referencedTable;
    }

    public static Column primaryKey(String name) {
        return new Column(name, DataType.INTEGER, true, true, null);
    }

    public static Column foreignKey(String name, String referencedTable) {
        if (referencedTable == null || referencedTable.trim().isEmpty()) {
            throw new IllegalArgumentException("Для внешнего ключа нужна таблица");
        }
        return new Column(name, DataType.INTEGER, true, false, referencedTable);
    }

    public String getName() { return name; }
    public DataType getType() { return type; }
    public boolean isRequired() { return required; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getReferencedTable() { return referencedTable; }

    Object normalize(Object value) {
        if (value == null && required) {
            throw new IllegalArgumentException("Столбец " + name + " обязателен");
        }
        return type.normalize(value, name);
    }
}
