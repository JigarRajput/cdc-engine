package com.jigar.engine;

import org.postgresql.PGConnection;
import org.postgresql.replication.PGReplicationStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class ReplicationConnection {
    
    private final String url = "jdbc:postgresql://localhost:5432/postgres";
    private final String user = "postgres";
    private final String password = "postgres";
    private final String slotName = "my_slot";
    private final String publication = "my_pub";

    public PGReplicationStream connect() throws Exception {
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("replication", "database");
        props.setProperty("assumeMinServerVersion", "9.4");
        props.setProperty("preferQueryMode", "simple");

        Connection con = DriverManager.getConnection(url, props);
        PGConnection pgCon = con.unwrap(PGConnection.class);

        return pgCon.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(slotName)
                .withSlotOption("proto_version", "1")
                .withSlotOption("publication_names", publication)
                .start();
    }
}