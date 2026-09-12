package quality.platform.lab.service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import quality.platform.lab.dto.Book;
import quality.platform.lab.dto.BookDetails;
import quality.platform.lab.dto.BookPatchRequest;

@Service
public class BookService {
    private final Map<UUID, Book> books = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final File dataFile;

    public BookService(JsonMapper jsonMapper, @Value("${books.data-file:data/books.json}") String dataFilePath) {
        this.jsonMapper = jsonMapper;
        this.dataFile = new File(dataFilePath);
        if (dataFile.exists() && dataFile.length() > 0) {
            List<Book> stored = jsonMapper.readValue(dataFile, new TypeReference<List<Book>>() {});
            stored.forEach(b -> books.put(b.getId(), b));
        }
    }

    private synchronized void persist() {
        File parent = dataFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        jsonMapper.writeValue(dataFile, books.values());
    }

    public Book getById(UUID id) {
        Book book = books.get(id);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + id);
        }
        return book;
    }

    public List<Book> search(String author, String title) {
        return books.values().stream()
                .filter(b -> author == null || author.equalsIgnoreCase(b.getAuthor()))
                .filter(b -> title == null || title.equalsIgnoreCase(b.getTitle()))
                .toList();
    }

    public Book create(Book request) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID());
        } else if (books.containsKey(request.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Book already exists: " + request.getId());
        }
        request.setPrice(scale(request.getPrice()));
        books.put(request.getId(), request);
        persist();
        return request;
    }

    public Book update(UUID id, BookPatchRequest request) {
        Book book = getById(id);
        if (request.getPrice() != null) {
            book.setPrice(scale(request.getPrice()));
        }
        if (request.getDetails() != null) {
            if (book.getDetails() == null) {
                book.setDetails(new BookDetails());
            }
            BookDetails target = book.getDetails();
            BookDetails source = request.getDetails();
            if (source.getAbout() != null) target.setAbout(source.getAbout());
            if (source.getReview() != null) target.setReview(source.getReview());
            if (source.getPages() != null) target.setPages(source.getPages());
            if (source.getEdition() != null) target.setEdition(source.getEdition());
        }
        persist();
        return book;
    }

    public void delete(UUID id) {
        if (books.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + id);
        }
        persist();
    }

    private BigDecimal scale(BigDecimal price) {
        return price == null ? null : price.setScale(2, RoundingMode.HALF_UP);
    }
}
