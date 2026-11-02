package app.service;

import app.dao.RoleDao;
import app.dao.jdbc.RoleDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Role;
import app.model.RoleType;

import java.util.List;

public class RoleService {
    private final RoleDao roleDao;

    public RoleService(ConnectionFactory factory) {
        this.roleDao = new RoleDaoJdbc(factory);
    }

    public void ensureBaseRoles() {
        roleDao.ensureRole(RoleType.ADMINISTRADOR);
        roleDao.ensureRole(RoleType.PROFESOR);
        roleDao.ensureRole(RoleType.ESTUDIANTE);
    }

    public List<Role> listRoles() {
        return roleDao.listAll();
    }
}
