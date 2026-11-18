package project.smartfinance.application.category;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.category.Category.Kind;

@Service
public class Categories {

    private final CategoryStore store;

    public Categories(CategoryStore store) {
        this.store = store;
    }

    @Transactional
    public Category create(int authenticatedUserId, String name, Kind kind) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nome obrigatório.");
        }
        return store.create(authenticatedUserId, trimmed, kind);
    }

    @Transactional(readOnly = true)
    public List<Category> list(int authenticatedUserId) {
        return store.listForOwner(authenticatedUserId);
    }

    @Transactional
    public Category setActive(int authenticatedUserId, int categoryId, boolean active) {
        return store.setActive(categoryId, authenticatedUserId, active)
                .orElseThrow(CategoryReferenceNotFoundException::new);
    }

    @Transactional
    public Category update(int ownerId, int categoryId, String name, Kind kind) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Nome obrigatório.");
        Category current = store.findForOwner(categoryId, ownerId)
                .orElseThrow(CategoryReferenceNotFoundException::new);
        if (current.kind() != kind && store.hasEntries(categoryId, ownerId)) {
            throw new CategoryInUseException();
        }
        return store.update(categoryId, ownerId, trimmed, kind)
                .orElseThrow(CategoryReferenceNotFoundException::new);
    }

    @Transactional
    public void delete(int ownerId, int categoryId) {
        store.findForOwner(categoryId, ownerId)
                .orElseThrow(CategoryReferenceNotFoundException::new);
        if (store.hasEntries(categoryId, ownerId)) throw new CategoryInUseException();
        if (!store.delete(categoryId, ownerId)) throw new CategoryReferenceNotFoundException();
    }
}
