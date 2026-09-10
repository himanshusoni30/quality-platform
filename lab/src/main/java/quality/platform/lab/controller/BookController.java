package quality.platform.lab.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import quality.platform.lab.dto.Book;
import quality.platform.lab.dto.BookPatchRequest;
import quality.platform.lab.service.BookService;

@RestController
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/book/{id}")
    public Book getBookById(@PathVariable UUID id) {
        return bookService.getById(id);
    }

    @GetMapping("/books")
    public List<Book> getBooks(@RequestParam(required = false) String author, @RequestParam(required = false) String title) {
        return bookService.search(author, title);
    }

    @PostMapping("/book")
    @ResponseStatus(HttpStatus.OK)
    public Book createBook(@RequestBody Book book) {
        return bookService.create(book);
    }

    @PatchMapping("/book/{id}")
    public Book updateBook(@PathVariable UUID id, @RequestBody BookPatchRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/book/{id}")
    public void deleteBook(@PathVariable UUID id) {
        bookService.delete(id);
    }
}
