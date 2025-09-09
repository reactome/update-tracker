package org.reactome.updateTracker;

import org.gk.persistence.MySQLAdaptor;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/4/2025
 */
public class Main {

    public static void main(String[] args) throws Exception {
        MySQLAdaptor sourceDBA = getSourceDBA();
        MySQLAdaptor currentSliceDBA = getCurrentSliceDBA();
        MySQLAdaptor previousSliceDBA = getPreviousSliceDBA();
        long personId = Long.parseLong(getConfigProps().getProperty("personId"));
        boolean uploadUpdateTrackerInstanceToSource = Boolean.getBoolean(getConfigProps().getProperty("uploadToSource"));

        UpdateTrackerHandler updateTrackerHandler =
            new UpdateTrackerHandler(sourceDBA, currentSliceDBA, previousSliceDBA, personId);
        updateTrackerHandler.handleUpdateTrackerInstances(uploadUpdateTrackerInstanceToSource);
    }

    private static MySQLAdaptor getSourceDBA() throws SQLException, IOException {
        return getDBA("curator");
    }

    private static MySQLAdaptor getCurrentSliceDBA() throws SQLException, IOException {
        return getDBA("currentslice");
    }

    private static MySQLAdaptor getPreviousSliceDBA() throws SQLException, IOException {
        return getDBA("previousslice");
    }

    private static MySQLAdaptor getDBA(String prefix) throws IOException, SQLException {
        Properties configProps = getConfigProps();
        String userName = configProps.getProperty(prefix + ".user", "root");
        String password = configProps.getProperty(prefix + ".password", "root");
        String dbName = configProps.getProperty(prefix + ".dbName");
        String host = configProps.getProperty(prefix + ".host", "localhost");
        int port = Integer.parseInt(configProps.getProperty(prefix + ".port", "3306"));

        return new MySQLAdaptor(host, dbName, userName, password, port);
    }

    private static Properties getConfigProps() throws IOException {
        Properties props = new Properties();
        props.load(Main.class.getClassLoader().getResourceAsStream("config.properties"));
        return props;
    }
}
