package com.zufe.procurement.m7.assistant.task;

import com.zufe.procurement.auth.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;

@Service
public class DraftCatalog {
    private final JdbcTemplate db;
    private final TaskAccess access;
    public DraftCatalog(JdbcTemplate db, TaskAccess access) { this.db = db; this.access = access; }
    public List<MaterialOption> materials() {
        access.require("m1:material:list");
        return db.query("SELECT m.id,m.material_no,m.material_name,m.spec,d.item_name FROM material m "
                + "JOIN dict_item d ON d.id=m.unit_id AND d.is_deleted=0 WHERE m.status=1 AND m.is_deleted=0 ORDER BY m.material_no",
                (row, index) -> new MaterialOption(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5)));
    }
    public List<OwnerOption> owners() {
        var user = AuthContext.getOrThrow();
        if (!user.hasPermission("m2:request_item:owner")) return List.of();
        var scope = user.getScope();
        if (!scope.isAll() && !scope.hasOwner()) return List.of();
        String query = "SELECT id,owner_type,owner_no,owner_name FROM owner_object WHERE status=1 AND is_deleted=0 AND owner_type IN (2,3)";
        List<Object> args = new ArrayList<>();
        if (!scope.isAll()) {
            query += " AND owner_no IN (" + String.join(",", Collections.nCopies(scope.getOwnerNos().size(), "?")) + ")";
            args.addAll(scope.getOwnerNos());
        }
        return db.query(query + " ORDER BY owner_no", (row, index) -> new OwnerOption(row.getLong(1), row.getInt(2), row.getString(3), row.getString(4)), args.toArray());
    }
}
