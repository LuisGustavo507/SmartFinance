package project.smartfinance.api.category;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.category.Categories;
import project.smartfinance.application.category.CategoryReferenceNotFoundException;
import project.smartfinance.application.category.CategoryInUseException;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.category.Category.Kind;

@RestController
@RequestMapping("/api/categories")
class CategoryController {

    private final Categories categories;

    CategoryController(Categories categories) {
        this.categories = categories;
    }

    @PostMapping
    ResponseEntity<Category> create(@Valid @RequestBody CreateCategoryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categories.create(ownerId, request.name(), request.kind()));
    }

    @GetMapping
    List<Category> list(Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return categories.list(ownerId);
    }

    @PatchMapping("/{id}/status")
    Category setActive(@PathVariable int id, @Valid @RequestBody CategoryStatusRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return categories.setActive(ownerId, id, request.active());
    }

    @PutMapping("/{id}")
    Category update(@PathVariable int id, @Valid @RequestBody CreateCategoryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return categories.update(ownerId, id, request.name(), request.kind());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        categories.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CategoryReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(CategoryInUseException.class)
    ResponseEntity<Void> inUse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidName() {
        return ResponseEntity.badRequest().build();
    }

    record CreateCategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull Kind kind) {
    }

    record CategoryStatusRequest(@NotNull Boolean active) {
    }
}
