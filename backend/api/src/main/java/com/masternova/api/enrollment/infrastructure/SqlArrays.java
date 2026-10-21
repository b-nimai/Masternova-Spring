package com.masternova.api.enrollment.infrastructure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

/**
 * Typed SQL arrays as statement parameters — what lets a whole batch travel as one {@code
 * unnest(…)} instead of N statements. Created on the statement's own connection (an array belongs
 * to one).
 */
final class SqlArrays {

  private SqlArrays() {}

  /**
   * @param type the element type as Postgres names it: {@code uuid}, {@code int4}, {@code bool},
   *     {@code timestamptz}
   */
  static <T> SqlTypeValue of(String type, List<T> rows, Function<T, Object> value) {
    return of(type, rows.stream().map(value).toArray());
  }

  static SqlTypeValue of(String type, Object[] values) {
    return new AbstractSqlTypeValue() {
      @Override
      protected Object createTypeValue(Connection con, int sqlType, String typeName)
          throws SQLException {
        return con.createArrayOf(type, values);
      }
    };
  }
}
