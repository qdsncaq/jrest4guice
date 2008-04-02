package org.jrest.dao.test.hibernate;

import java.util.List;

import org.jrest.dao.annotations.Create;
import org.jrest.dao.annotations.Delete;
import org.jrest.dao.annotations.Find;
import org.jrest.dao.annotations.Retrieve;
import org.jrest.dao.annotations.Update;
import org.jrest.dao.annotations.Find.FirstResult;
import org.jrest.dao.annotations.Find.MaxResults;
import org.jrest.dao.test.entities.Book;
import org.jrest.dao.test.entities.BookModel;
import org.jrest.dao.test.entities.Category;

import com.google.inject.name.Named;

public interface TestDao {
	
	@Create
	void createBook(Book book);

	@Retrieve
	Book loadBook(String pk);
	
	@Update
	void updateBook(Book book);
	
	@Delete
	void deleteBook(Book book);
	
	@Find(query = "from Book b")
	List<Book> allBooks();
	
	@Find(query = "from Book b where b.price > ?")
	List<Book> findBooksPriceMoreThan(float price, @FirstResult int start, @MaxResults int max);

	@Find(query = "from Book b where b.price > ?")
	List<Book> findBooksPriceMoreThan(float price);
	
	@Find(namedQuery = "Book.lengthMoreThan")
	List<Book> lengthMoreThan(@Named("length")int i);
	
	@Find(query = "select b.id, b.title, b.price from Book b where b.price > :price", resultClass = BookModel.class)
	List<BookModel> findBookModelsPriceMoreThan(@Named("price")float price);
	
	@Create
	void createCategory(Category category);
	
	@Retrieve
	Category loadCategory(String pk);
	
	@Update
	void updateCategory(Category category);
	
	@Delete
	void deleteCategory(Category category);
}
