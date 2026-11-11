import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Database implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Table> tables;

    public Database(List<Table> tables) {
        if (tables == null) {
            throw new IllegalArgumentException("Список таблиц обязателен");
        }
        this.tables = new ArrayList<Table>();
        Set<String> names = new HashSet<String>();
        for (Table table : tables) {
            if (table == null || !names.add(table.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Пустая или повторная таблица");
            }
            this.tables.add(new Table(table.getName(), table.getColumns(), table.getRows()));
        }
        validateState(null, Collections.<Row>emptyList());
    }

    public List<Table> getTables() { return Collections.unmodifiableList(tables); }

    public Table getTable(String name) {
        for (Table table : tables) {
            if (table.getName().equalsIgnoreCase(name)) {
                return table;
            }
        }
        throw new IllegalArgumentException("Неизвестная таблица " + name);
    }

    public void insert(String tableName, Row row) {
        insertAll(tableName, Collections.singletonList(row));
    }

    public void insert(String tableName, Map<String, Object> values) {
        Table table = getTable(tableName);
        if (values == null) {
            throw new IllegalArgumentException("Значения для вставки обязательны");
        }
        List<Object> rowValues = new ArrayList<Object>(
                Collections.nCopies(table.getColumns().size(), (Object) null));
        Set<Integer> assigned = new HashSet<Integer>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            int index = table.columnIndex(entry.getKey());
            if (!assigned.add(index)) {
                throw new IllegalArgumentException("Повторный столбец " + entry.getKey());
            }
            rowValues.set(index, entry.getValue());
        }
        insert(tableName, new Row(rowValues));
    }

    public int insertAll(String tableName, List<Row> rows) {
        Table table = getTable(tableName);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Нужна хотя бы одна запись");
        }
        List<Row> candidate = new ArrayList<Row>(table.getRows());
        for (Row row : rows) {
            candidate.add(table.normalizeRow(row));
        }
        validateAndReplace(table, candidate);
        return rows.size();
    }

    public List<Row> select(String tableName, Condition condition) {
        Table table = getTable(tableName);
        List<Row> rows = table.getRows();
        List<Row> result = new ArrayList<Row>();
        for (int index : matchingIndexes(table, condition)) {
            result.add(rows.get(index));
        }
        return Collections.unmodifiableList(result);
    }

    public int update(String tableName, String columnName, Object value, Condition condition) {
        Table table = getTable(tableName);
        int columnIndex = table.columnIndex(columnName);
        Object normalized = table.getColumns().get(columnIndex).normalize(value);
        List<Integer> matches = matchingIndexes(table, condition);
        if (matches.isEmpty()) {
            return 0;
        }
        List<Row> candidate = new ArrayList<Row>(table.getRows());
        for (int index : matches) {
            List<Object> values = new ArrayList<Object>(candidate.get(index).getValues());
            values.set(columnIndex, normalized);
            candidate.set(index, new Row(values));
        }
        validateAndReplace(table, candidate);
        return matches.size();
    }

    public int delete(String tableName, Condition condition) {
        Table table = getTable(tableName);
        Set<Integer> matches = new HashSet<Integer>(matchingIndexes(table, condition));
        if (matches.isEmpty()) {
            return 0;
        }
        List<Row> rows = table.getRows();
        List<Row> candidate = new ArrayList<Row>();
        for (int i = 0; i < rows.size(); i++) {
            if (!matches.contains(i)) {
                candidate.add(rows.get(i));
            }
        }
        validateAndReplace(table, candidate);
        return matches.size();
    }

    private List<Integer> matchingIndexes(Table table, Condition condition) {
        List<Integer> result = new ArrayList<Integer>();
        List<Row> rows = table.getRows();
        if (condition == null) {
            for (int i = 0; i < rows.size(); i++) {
                result.add(i);
            }
            return result;
        }
        int columnIndex = table.columnIndex(condition.getColumn());
        Column column = table.getColumns().get(columnIndex);
        Object expected = condition.normalizeValue(column);
        for (int i = 0; i < rows.size(); i++) {
            if (condition.matches(rows.get(i).getValue(columnIndex), expected, column.getType())) {
                result.add(i);
            }
        }
        return result;
    }

    private void validateAndReplace(Table table, List<Row> candidate) {
        validateState(table, candidate);
        table.replaceRows(candidate);
    }

    private void validateState(Table changedTable, List<Row> candidate) {
        Map<Table, Set<Object>> keysByTable = new HashMap<Table, Set<Object>>();
        for (Table table : tables) {
            List<Row> rows = table == changedTable ? candidate : table.getRows();
            Set<Object> keys = new HashSet<Object>();
            for (Row row : rows) {
                Object key = row.getValue(table.getPrimaryKeyIndex());
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Повторный ключ " + key
                            + " в таблице " + table.getName());
                }
            }
            keysByTable.put(table, keys);
        }

        for (Table table : tables) {
            List<Row> rows = table == changedTable ? candidate : table.getRows();
            List<Column> columns = table.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                if (column.getReferencedTable() != null) {
                    Table target = getTable(column.getReferencedTable());
                    Set<Object> targetKeys = keysByTable.get(target);
                    for (Row row : rows) {
                        Object key = row.getValue(i);
                        if (!targetKeys.contains(key)) {
                            throw new IllegalArgumentException("Запись в " + table.getName()
                                    + " ссылается на отсутствующий ключ " + key
                                    + " в таблице " + target.getName());
                        }
                    }
                }
                if (table.getName().equalsIgnoreCase("cars")
                        && column.getName().equalsIgnoreCase("price")
                        && column.getType() == DataType.DOUBLE) {
                    for (Row row : rows) {
                        Double price = (Double) row.getValue(i);
                        if (price != null && price < 0) {
                            throw new IllegalArgumentException("Цена не может быть отрицательной");
                        }
                    }
                }
            }
        }
    }
}
