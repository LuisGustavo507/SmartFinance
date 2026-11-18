package project.smartfinance.application.category;

import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.category.Category.Kind;

public interface CategoryStore {

    Category create(int ownerUserId, String name, Kind kind);

    List<Category> listForOwner(int ownerUserId);

    Optional<Category> findForOwner(int id, int ownerUserId);

    Optional<Category> setActive(int id, int ownerUserId, boolean active);

    boolean hasEntries(int id, int ownerUserId);

    Optional<Category> update(int id, int ownerUserId, String name, Kind kind);

    boolean delete(int id, int ownerUserId);
}
