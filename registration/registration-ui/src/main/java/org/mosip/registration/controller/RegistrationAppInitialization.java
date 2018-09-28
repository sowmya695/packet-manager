package org.mosip.registration.controller;

import static org.mosip.registration.constants.RegConstants.APPLICATION_ID;
import static org.mosip.registration.constants.RegConstants.APPLICATION_NAME;
import static org.mosip.registration.constants.RegistrationUIExceptionEnum.REG_UI_LOGIN_NULLPOINTER_EXCEPTION;

import java.text.SimpleDateFormat;

import org.mosip.kernel.core.spi.logging.MosipLogger;
import org.mosip.kernel.logger.appenders.MosipRollingFileAppender;
import org.mosip.kernel.logger.factory.MosipLogfactory;
import org.mosip.registration.config.AppConfig;
import org.mosip.registration.constants.RegistrationUIExceptionCode;
import org.mosip.registration.exception.RegBaseCheckedException;
import org.mosip.registration.exception.RegBaseUncheckedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

@Component
public class RegistrationAppInitialization extends Application {

	/**
	 * Instance of {@link MosipLogger}
	 */
	private static MosipLogger LOGGER;

	@Autowired
	private void initializeLogger(MosipRollingFileAppender mosipRollingFileAppender) {
		LOGGER = MosipLogfactory.getMosipDefaultRollingFileLogger(mosipRollingFileAppender, this.getClass());
	}

	public static ApplicationContext applicationContext;
	public static Scene scene;

	LoginController loginController;

	/*
	 * Load the initial screen by getting values form the database. Maintaining the
	 * same Stage for all the scenes.
	 */

	@Override
	public void start(Stage primaryStage) throws RegBaseCheckedException {
		LOGGER.debug("REGISTRATION - LOGIN SCREEN INITILIZATION - REGISTRATIONAPPINITILIZATION", APPLICATION_NAME,
				APPLICATION_ID,
				"Login screen initilization " + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()));

		BaseController.stage = primaryStage;
		primaryStage = BaseController.getStage();

		loginController = applicationContext.getBean(LoginController.class);
		String loginMode = loginController.loadInitialScreen();
		String loginModeFXMLpath = null;
		try {
			BorderPane loginRoot = BaseController.load(getClass().getResource("/fxml/RegistrationLogin.fxml"));
			if (loginMode.equals("OTP")) {
				loginModeFXMLpath = "/fxml/LoginWithOTP.fxml";
				AnchorPane loginType = BaseController.load(getClass().getResource(loginModeFXMLpath));
				loginRoot.setCenter(loginType);
			} else if (loginMode.equals("PWD")) {
				loginModeFXMLpath = "/fxml/LoginWithCredentials.fxml";
				AnchorPane loginType = BaseController.load(getClass().getResource(loginModeFXMLpath));
				loginRoot.setCenter(loginType);
			}
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			scene = new Scene(loginRoot, 950, 630);
			scene.getStylesheets().add(loader.getResource("application.css").toExternalForm());

			primaryStage.setResizable(false);
			primaryStage.setScene(scene);
			primaryStage.show();

		} catch (NullPointerException nullPointerException) {
			throw new RegBaseCheckedException(REG_UI_LOGIN_NULLPOINTER_EXCEPTION.getErrorCode(),
					REG_UI_LOGIN_NULLPOINTER_EXCEPTION.getErrorMessage());
		} catch (RuntimeException runtimeException) {
			throw new RegBaseUncheckedException(RegistrationUIExceptionCode.REG_UI_LOGIN_LOADER_EXCEPTION,
					runtimeException.getMessage());
		}

		LOGGER.debug("REGISTRATION - LOGIN SCREEN INITILIZATION - REGISTRATIONAPPINITILIZATION", APPLICATION_NAME,
				APPLICATION_ID,
				"Login screen loaded" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()));

	}

	public static void main(String[] args) {

		applicationContext = new AnnotationConfigApplicationContext(AppConfig.class);
		launch(args);
		LOGGER.debug("REGISTRATION - APPLICATION INITILIZATION - REGISTRATIONAPPINITILIZATION", APPLICATION_NAME,
				APPLICATION_ID,
				"Application Initilization" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()));

	}
}
