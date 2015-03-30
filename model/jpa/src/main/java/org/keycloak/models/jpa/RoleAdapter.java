package org.keycloak.models.jpa;

import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.jpa.entities.RoleAttributeEntity;
import org.keycloak.models.jpa.entities.RoleEntity;
import org.keycloak.models.jpa.entities.UserAttributeEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

import javax.persistence.EntityManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @author <a href="mailto:jli@vizuri.com">Jiehuan Li</a>
 * @version $Revision: 1 $
 */
public class RoleAdapter implements RoleModel {
    protected RoleEntity role;
    protected EntityManager em;
    protected RealmModel realm;

    public RoleAdapter(RealmModel realm, EntityManager em, RoleEntity role) {
        this.em = em;
        this.realm = realm;
        this.role = role;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    @Override
    public String getName() {
        return role.getName();
    }

    @Override
    public String getDescription() {
        return role.getDescription();
    }

    @Override
    public void setDescription(String description) {
        role.setDescription(description);
    }

    @Override
    public String getId() {
        return role.getId();
    }

    @Override
    public void setName(String name) {
        role.setName(name);
    }

    @Override
    public boolean isComposite() {
        return getComposites().size() > 0;
    }

    @Override
    public void addCompositeRole(RoleModel role) {
        RoleEntity entity = RoleAdapter.toRoleEntity(role, em);
        for (RoleEntity composite : getRole().getCompositeRoles()) {
            if (composite.equals(entity)) return;
        }
        getRole().getCompositeRoles().add(entity);
        em.flush();
    }

    @Override
    public void removeCompositeRole(RoleModel role) {
        RoleEntity entity = RoleAdapter.toRoleEntity(role, em);
        Iterator<RoleEntity> it = getRole().getCompositeRoles().iterator();
        while (it.hasNext()) {
            if (it.next().equals(entity)) it.remove();
        }
    }

    @Override
    public Set<RoleModel> getComposites() {
        Set<RoleModel> set = new HashSet<RoleModel>();

        for (RoleEntity composite : getRole().getCompositeRoles()) {
           set.add(new RoleAdapter(realm, em, composite));
        }
        return set;
    }

    @Override
    public boolean hasRole(RoleModel role) {
        if (this.equals(role)) return true;
        if (!isComposite()) return false;

        Set<RoleModel> visited = new HashSet<RoleModel>();
        return KeycloakModelUtils.searchFor(role, this, visited);
    }

    @Override
    public RoleContainerModel getContainer() {
        if (role.isApplicationRole()) {
            return realm.getApplicationById(role.getApplication().getId());

        } else {
            return realm;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof RoleModel)) return false;

        RoleModel that = (RoleModel) o;
        return that.getId().equals(getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    public static RoleEntity toRoleEntity(RoleModel model, EntityManager em) {
        if (model instanceof RoleAdapter) {
            return ((RoleAdapter)model).getRole();
        }
        return em.getReference(RoleEntity.class, model.getId());
    }
    
    @Override
    public void setAttribute(String name, String value) {
        for (RoleAttributeEntity attr : role.getAttributes()) {
            if (attr.getName().equals(name)) {
                attr.setValue(value);
                return;
            }
        }
        RoleAttributeEntity attr = new RoleAttributeEntity();
        attr.setName(name);
        attr.setValue(value);
        attr.setRole(role);
        em.persist(attr);
        role.getAttributes().add(attr);
    }

    @Override
    public void removeAttribute(String name) {
        Iterator<RoleAttributeEntity> it = role.getAttributes().iterator();
        while (it.hasNext()) {
            RoleAttributeEntity attr = it.next();
            if (attr.getName().equals(name)) {
                it.remove();
                em.remove(attr);
            }
        }
    }

    @Override
    public String getAttribute(String name) {
        for (RoleAttributeEntity attr : role.getAttributes()) {
            if (attr.getName().equals(name)) {
                return attr.getValue();
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getAttributes() {
        Map<String, String> result = new HashMap<String, String>();
        for (RoleAttributeEntity attr : role.getAttributes()) {
            result.put(attr.getName(), attr.getValue());
        }
        return result;
    }
    
    @Override
    public String getFederationLink() {
        return role.getFederationLink();
    }

    @Override
    public void setFederationLink(String link) {
        role.setFederationLink(link);
    }
}
