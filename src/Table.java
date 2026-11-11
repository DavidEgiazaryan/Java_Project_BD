import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Table implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Column> columns;
    private final int primaryKeyIndex;
    private List<Row> rows;

    public Table(String name, List<Column> columns, List<Row> rows) {
        if (name == null || name.trim().isEmpty() || columns == null || rows == null) {
            throw new IllegalArgumentException("Имя, столбцы и записи обязательны");
        }
        this.name = name;
        this.columns = new ArrayList<Column>(columns);
        Set<String> names = new HashSet<String>();
        int keyIndex = -1;
        for (int i = 0; i < this.columns.size(); i++) {
            Column column = this.columns.get(i);
            if (column == null || !names.add(column.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Пустой или повторный столбец в таблице " + name);
            }
            if (column.isPrimaryKey()) {
                if (keyIndex >= 0) {
                    throw new IllegalArgumentException("В таблице " + name + " больше одного первичного ключа");
                }
                keyIndex = i;
            }
        }
        if (keyIndex < 0) {
            throw new IllegalArgumentException("У таблицы " + name + " нет первичного ключа");
        }
        this.primaryKeyIndex = keyIndex;
        this.rows = new ArrayList<Row>();
        for (Row row : rows) {
            this.rows.add(normalizeRow(row));
        }
    }

    public String getName() { return name; }

    public List<Column> getColumns() { return Collections.unmodifiableList(columns); }

    public List<Row> getRows() { return Collections.unmodifiableList(rows); }

    int getPrimaryKeyIndex() { return primaryKeyIndex; }

    int columnIndex(String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Неизвестный столбец " + columnName + " в таблице " + name);
    }

    Row normalizeRow(Row row) {
        if (row == null) {
            throw new IllegalArgumentException("Запись отсутствует");
        }
        List<Object> values = row.getValues();
        if (values.size() != columns.size()) {
            throw new IllegalArgumentException("Неверное число значений для таблицы " + name);
        }
        List<Object> normalized = new ArrayList<Object>();
        for (int i = 0; i < columns.size(); i++) {
            normalized.add(columns.get(i).normalize(values.get(i)));
        }
        return new Row(normalized);
    }

    void replaceRows(List<Row> validatedRows) {
        rows = validatedRows;
    }
}
