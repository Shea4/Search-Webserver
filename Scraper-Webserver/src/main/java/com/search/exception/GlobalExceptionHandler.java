package com.search.exception;

import com.search.entities.JsonResponseEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<String> handleNotFound(HttpServletResponse response, NoHandlerFoundException exception) {
		return new JsonResponseEntity("You've reached a dead end", 404);
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<String> handleException(Throwable exception) {
		while (exception.getCause() != null) {
			exception = exception.getCause();
		}

		return new JsonResponseEntity(exception.getMessage(), 500);
	}

}