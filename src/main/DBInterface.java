package main;

import arc.util.Log;

import java.sql.*;
import java.util.HashMap;

public class DBInterface {
    public Connection conn = null;

    private PreparedStatement preparedStatement = null;

    public void connect(String db, String username, String password) {
        // SQLite connection string
        String url = "jdbc:mysql://127.0.0.1:3306/" + db + "?useSSL=false";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(url, username, password);
            Log.info("Connected to database successfully");
        } catch (SQLException | ClassNotFoundException e) {
            Log.err(e);
        }
    }

    public boolean hasRow(String table, String key, Object val) {
        return hasRow(table, new String[] { key }, new Object[] { val });
    }

    public boolean hasRow(String table, String[] keys, Object[] vals) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + table + " WHERE ");
        for (int i = 0; i < keys.length; i++) {
            sql.append(keys[i]).append(" = ?").append(i < keys.length - 1 ? " AND " : "");
        }
        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }

            ResultSet rs = preparedStatement.executeQuery();
            return rs.last();
        } catch (SQLException e) {
            Log.err(e);
        }
        return false;
    }

    public void addEmptyRow(String table, String key, Object val) {
        addEmptyRow(table, new String[] { key }, new Object[] { val });
    }

    public void addEmptyRow(String table, String[] keys, Object[] vals) {
        String sql = "INSERT INTO " + table + " (";
        StringBuilder keyString = new StringBuilder();
        StringBuilder valString = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            keyString.append(keys[i]).append(i < keys.length - 1 ? ", " : "");
            valString.append("? ").append(i < keys.length - 1 ? ", " : "");
        }
        sql += keyString + ") VALUES(" + valString + ")";
        try {
            preparedStatement = conn.prepareStatement(sql);
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public HashMap<String, Object> loadRow(String table, String[] keys, Object[] vals) {
        HashMap<String, Object> returnedVals = new HashMap<String, Object>();

        if (!hasRow(table, keys, vals))
            addEmptyRow(table, keys, vals);
        StringBuilder sql = new StringBuilder("SELECT * FROM " + table + " WHERE ");
        for (int i = 0; i < keys.length; i++) {
            sql.append(keys[i]).append(" = ?").append(i < keys.length - 1 ? " AND " : "");
        }

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            for (int i = 0; i < vals.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }
            ResultSet rs = preparedStatement.executeQuery();
            rs.next();
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) { // ONE INDEXED? REALLY?
                returnedVals.put(rsmd.getColumnName(i), rs.getObject(rsmd.getColumnName(i)));
            }
            rs.close();
        } catch (SQLException e) {
            Log.err(e);
        }

        return returnedVals;

    }

    public void saveRow(String table, String searchKey, Object searchVal, String[] keys, Object[] vals) {
        saveRow(table, new String[] { searchKey }, new Object[] { searchVal }, keys, vals);
    }

    public void saveRow(String table, String[] searchKeys, Object[] searchVals, String[] keys, Object[] vals) {
        StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
        for (int i = 0; i < keys.length; i++) {
            sql.append(keys[i]).append(" = ?").append(i < keys.length - 1 ? ", " : "");
        }
        sql.append(" WHERE ");
        for (int i = 0; i < searchKeys.length; i++) {
            sql.append(searchKeys[i]).append(" = ?").append(i < searchKeys.length - 1 ? " AND " : "");
        }
        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            for (int i = 0; i < keys.length; i++) {
                preparedStatement.setObject(i + 1, vals[i]);
            }
            for (int i = 0; i < searchKeys.length; i++) {
                preparedStatement.setObject(i + keys.length + 1, searchVals[i]);
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            Log.err(e);
        }

    }
}