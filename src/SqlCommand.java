import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SqlCommand {
    private final String tableName;

    private SqlCommand(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public static final class Insert extends SqlCommand {
        private final List<Row> rows;

        Insert(String tableName, List<Row> rows) {
            super(tableName);
            this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
        }

        public List<Row> getRows() {
            return rows;
        }
    }

    public static final class Select extends SqlCommand {
        private final List<String> columns;
        private final Condition condition;

        Select(String tableName, List<String> columns, Condition condition) {
            super(tableName);
            this.columns = Collections.unmodifiableList(new ArrayList<String>(columns));
            this.condition = condition;
        }

        public List<String> getColumns() {
            return columns;
        }

        public Condition getCondition() {
            return condition;
        }
    }

    public static final class Update extends SqlCommand {
        private final String column;
        private final Object value;
        private final Condition condition;

        Update(String tableName, String column, Object value, Condition condition) {
            super(tableName);
            this.column = column;
            this.value = Row.copyValue(value);
            this.condition = condition;
        }

        public String getColumn() {
            return column;
        }

        public Object getValue() {
            return Row.copyValue(value);
        }

        public Condition getCondition() {
            return condition;
        }
    }

    public static final class Delete extends SqlCommand {
        private final Condition condition;

        Delete(String tableName, Condition condition) {
            super(tableName);
            this.condition = condition;
        }

        public Condition getCondition() {
            return condition;
        }
    }
}
