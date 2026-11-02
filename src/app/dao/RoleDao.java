package app.dao;

import app.model.Role;
import app.model.RoleType;

import java.util.List;

public interface RoleDao {
    Role findByName(RoleType name);
    List<Role> listAll();
    long ensureRole(RoleType name);
}
