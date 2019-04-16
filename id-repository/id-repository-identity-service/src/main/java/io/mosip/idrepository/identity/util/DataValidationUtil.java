package io.mosip.idrepository.identity.util;

import org.springframework.validation.Errors;

import io.mosip.idrepository.core.exception.IdRepoDataValidationException;

/**
 * The Class DataValidationUtil.
 *
 * @author Manoj SP
 */
public final class DataValidationUtil {

	/**
	 * Instantiates a new data validation util.
	 */
	private DataValidationUtil() {
	}

	/**
	 * Get list of errors from error object
	 *
	 * @param errors the errors
	 * @throws IdRepoDataValidationException the IdRepoDataValidationException
	 */
	public static void validate(Errors errors) throws IdRepoDataValidationException {
		if (errors.hasErrors()) {
			IdRepoDataValidationException exception = new IdRepoDataValidationException();
			errors.getAllErrors().forEach(error -> exception.addInfo(error.getCode(), error.getDefaultMessage()));
			throw exception;
		}
	}

}
