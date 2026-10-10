
package com.remarket.remarket.controller;

import com.remarket.remarket.entity.Product;
import com.remarket.remarket.entity.User;
import com.remarket.remarket.repository.ProductRepository;
import com.remarket.remarket.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@CrossOrigin
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    public ProductController(
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // Upload a product image
    @PostMapping(
            value = "/upload-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProductImage(
            @RequestParam("image") MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select an image.");
        }

        if (image.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body("Image size must be 5 MB or less.");
        }

        String contentType = image.getContentType();

        if (contentType == null ||
                !contentType.toLowerCase(Locale.ROOT)
                        .startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body("Please upload a valid image.");
        }

        String originalName = image.getOriginalFilename();

        if (originalName == null ||
                originalName.lastIndexOf('.') < 0) {
            return ResponseEntity.badRequest()
                    .body("The image must have a valid file extension.");
        }

        String extension = originalName
                .substring(originalName.lastIndexOf('.'))
                .toLowerCase(Locale.ROOT);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest()
                    .body("Allowed formats: JPG, JPEG, PNG, WEBP and GIF.");
        }

        try {
            Path uploadDirectory = Paths.get(
                    System.getProperty("user.dir"),
                    "src", "main", "resources", "static", "uploads");

            Files.createDirectories(uploadDirectory);

            String fileName = UUID.randomUUID() + extension;
            Path destination = uploadDirectory.resolve(fileName);

            Files.copy(image.getInputStream(), destination);

            Map<String, String> result = new HashMap<>();
            result.put("imageName", "/uploads/" + fileName);

            return ResponseEntity.ok(result);

        } catch (IOException exception) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to save the product image.");
        }
    }

    // Add a product
    @PostMapping
    public ResponseEntity<?> addProduct(@RequestBody Product product) {

        if (product.getSellerEmail() == null ||
                !userRepository.existsByEmail(product.getSellerEmail())) {
            return ResponseEntity.badRequest()
                    .body("A valid seller account is required.");
        }

        return ResponseEntity.ok(productRepository.save(product));
    }

    // Get all products
    @GetMapping
    public ResponseEntity<?> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    // Get one product and its seller contact details
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {

        Optional<Product> productOptional = productRepository.findById(id);

        if (productOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found.");
        }

        Product product = productOptional.get();

        Map<String, Object> result = new HashMap<>();
        result.put("id", product.getId());
        result.put("productName", product.getProductName());
        result.put("category", product.getCategory());
        result.put("price", product.getPrice());
        result.put("location", product.getLocation());
        result.put("description", product.getDescription());
        result.put("imageName", product.getImageName());

        Optional<User> sellerOptional =
                userRepository.findByEmail(product.getSellerEmail());

        if (sellerOptional.isPresent()) {
            User seller = sellerOptional.get();
            result.put("sellerName", seller.getName());
            result.put("sellerEmail", seller.getEmail());
            result.put("sellerPhone", seller.getPhone());
        } else {
            result.put("sellerName", "Seller information unavailable");
            result.put("sellerEmail", null);
            result.put("sellerPhone", null);
        }

        return ResponseEntity.ok(result);
    }

    // Get products belonging to a seller
    @GetMapping("/seller/{email}")
    public ResponseEntity<?> getProductsBySeller(
            @PathVariable String email) {
        return ResponseEntity.ok(
                productRepository.findBySellerEmail(email));
    }

    // Update a product only when its stored seller email matches

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestHeader("X-User-Email") String sellerEmail,
            @RequestBody Product updatedProduct) {

        Optional<Product> existingProduct =
                productRepository.findByIdAndSellerEmail(id, sellerEmail);

        if (existingProduct.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found or you do not own this product.");
        }

        Product product = existingProduct.get();

        product.setProductName(updatedProduct.getProductName());
        product.setCategory(updatedProduct.getCategory());
        product.setPrice(updatedProduct.getPrice());
        product.setLocation(updatedProduct.getLocation());
        product.setDescription(updatedProduct.getDescription());

        // Preserve the existing image unless a new image is provided.
        if (updatedProduct.getImageName() != null
                && !updatedProduct.getImageName().trim().isEmpty()) {
            product.setImageName(updatedProduct.getImageName());
        }

        // Keep the original seller email and existing image unchanged otherwise.
        return ResponseEntity.ok(productRepository.save(product));
    }


    // Delete a product only when its stored seller email matches
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Long id,
            @RequestHeader("X-User-Email") String sellerEmail) {

        Optional<Product> product =
                productRepository.findByIdAndSellerEmail(id, sellerEmail);

        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found or you do not own this product.");
        }

        productRepository.delete(product.get());

        return ResponseEntity.ok("Product deleted successfully.");
    }
}
