package io.mosip.registration.processor.stages.uingenerator.stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.mosip.kernel.core.fsadapter.exception.FSAdapterException;
import io.mosip.kernel.core.fsadapter.spi.FileSystemAdapter;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleManager;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.constant.EventId;
import io.mosip.registration.processor.core.constant.EventName;
import io.mosip.registration.processor.core.constant.EventType;
import io.mosip.registration.processor.core.constant.JsonConstant;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.PacketFiles;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.util.PacketStructure;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.packet.dto.ApplicantDocument;
import io.mosip.registration.processor.core.packet.dto.FieldValueArray;
import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.core.packet.dto.PacketMetaInfo;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.identify.RegistrationProcessorIdentity;
import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.util.IdentityIteratorUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import io.mosip.registration.processor.packet.storage.entity.IndividualDemographicDedupeEntity;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.stages.uingenerator.dto.UinResponseDto;
import io.mosip.registration.processor.stages.uingenerator.idrepo.dto.Documents;
import io.mosip.registration.processor.stages.uingenerator.idrepo.dto.IdRequestDto;
import io.mosip.registration.processor.stages.uingenerator.idrepo.dto.IdResponseDTO;
import io.mosip.registration.processor.stages.uingenerator.idrepo.dto.RequestDto;
import io.mosip.registration.processor.stages.uingenerator.util.UinStatusMessage;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.code.RegistrationType;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * The Class UinGeneratorStage.
 * 
 * @author Ranjitha Siddegowda
 * @author Rishabh Keshari
 */
@Service
public class UinGeneratorStage extends MosipVerticleManager {

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(UinGeneratorStage.class);

	/** The Constant USER. */
	private static final String USER = "MOSIP_SYSTEM";

	/** The mosip event bus. */
	MosipEventBus mosipEventBus = null;

	/** The cluster address. */
	@Value("${registration.processor.vertx.cluster.address}")
	private String clusterAddress;

	/** The localhost. */
	@Value("${registration.processor.vertx.localhost}")
	private String localhost;

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** The id repo create. */
	@Value("${registration.processor.id.repo.create}")
	private String idRepoCreate;

	/** The id repo update. */
	@Value("${registration.processor.id.repo.update}")
	private String idRepoUpdate;

	/** The adapter. */
	@Autowired
	private FileSystemAdapter adapter;

	/** The core audit request builder. */
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	/** The registration processor rest client service. */
	@Autowired
	RegistrationProcessorRestClientService<Object> registrationProcessorRestClientService;

	/** The demographic dedupe repository. */
	@Autowired
	private BasePacketRepository<IndividualDemographicDedupeEntity, String> demographicDedupeRepository;

	/** The packet info manager. */
	@Autowired
	private PacketInfoManager<Identity, ApplicantInfoDto> packetInfoManager;

	/** The registration id. */
	private String registrationId = "";

	/** The id response DTO. */
	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = "\\";

	/** The id response DTO. */
	IdResponseDTO idResponseDTO = new IdResponseDTO();

	/** The id request DTO. */
	IdRequestDto idRequestDTO = new IdRequestDto();

	/** The identity json. */
	JSONObject identityJson = null;

	/** The demographic identity. */
	JSONObject demographicIdentity = null;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/** The id repo api version. */
	private String idRepoApiVersion = "1.0";

	/** The reg processor identity json. */
	@Autowired
	private RegistrationProcessorIdentity regProcessorIdentityJson;

	/** The utility. */
	@Autowired
	private Utilities utility;

	/** The identity iterator util. */
	IdentityIteratorUtil identityIteratorUtil = new IdentityIteratorUtil();
	
	/** The Constant UIN_FAILURE. */
	private static final String UIN_FAILURE = "UIN updation failure for registrationId ";
	
	/** The Constant NULL_IDREPO_RESPONSE. */
	private static final String NULL_IDREPO_RESPONSE = "Response from IdRepo is null";
	
	/** The description. */
	private String description = "";
	
	/** The is transaction successful. */
	private boolean isTransactionSuccessful = false;
	
	/** The Constant UIN_GENERATION_FAILED. */
	private static final String UIN_GENERATION_FAILED = "UIN Generation failed :";
	
	/** The registration status dto. */
	InternalRegistrationStatusDto registrationStatusDto=null;
	
	private static final String UIN = "UIN";
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.processor.core.spi.eventbus.EventBusManager#process(
	 * java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public MessageDTO process(MessageDTO object) {

		object.setMessageBusAddress(MessageBusAddress.UIN_GENERATION_BUS_IN);
		object.setInternalError(Boolean.FALSE);
		object.setIsValid(true);
		this.registrationId = object.getRid();
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				registrationId, "UinGeneratorStage::process()::entry");
		UinResponseDto uinResponseDto = null;
		registrationStatusDto = registrationStatusService.getRegistrationStatus(registrationId);
		try {

			InputStream idJsonStream = adapter.getFile(registrationId,
					PacketFiles.DEMOGRAPHIC.name() + FILE_SEPARATOR + PacketFiles.ID.name());
			byte[] idJsonBytes = IOUtils.toByteArray(idJsonStream);
			String getJsonStringFromBytes = new String(idJsonBytes);
			identityJson = (JSONObject) JsonUtil.objectMapperReadValue(getJsonStringFromBytes, JSONObject.class);
			demographicIdentity = JsonUtil.getJSONObject(identityJson, utility.getGetRegProcessorDemographicIdentity());
			Long uinFieldCheck = (Long) JsonUtil.getJSONValue(demographicIdentity, UIN);
			if (uinFieldCheck == null) {
				String test = (String) registrationProcessorRestClientService.getApi(ApiName.UINGENERATOR, null, "", "",
						String.class);
				Gson gsonObj = new Gson();
				uinResponseDto = gsonObj.fromJson(test, UinResponseDto.class);
				long uinInLong = Long.parseLong(uinResponseDto.getUin());
				demographicIdentity.put("UIN", uinInLong);
				idResponseDTO = sendIdRepoWithUin(registrationId, uinResponseDto.getUin());
				if (idResponseDTO != null && idResponseDTO.getResponse() != null) {
					demographicDedupeRepository.updateUinWrtRegistraionId(registrationId, uinResponseDto.getUin());
					registrationStatusDto.setStatusComment(UinStatusMessage.PACKET_UIN_UPDATION_SUCCESS_MSG);
					registrationStatusDto.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_SUCCESS.toString());
					isTransactionSuccessful = true;
					description = "UIN updated succesfully for registrationId " + registrationId;
					regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							"UinGeneratorStage::process()::exit");
					regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId, description);
				} else {
					String statusComment = idResponseDTO != null && idResponseDTO.getErrors() != null
							? idResponseDTO.getErrors().get(0).getErrorMessage()
							: NULL_IDREPO_RESPONSE;
					registrationStatusDto.setStatusComment(statusComment);
					object.setInternalError(Boolean.TRUE);
					registrationStatusDto.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());
					isTransactionSuccessful = false;
					description = UIN_FAILURE + registrationId + "::" + idResponseDTO != null
							&& idResponseDTO.getErrors() != null ? idResponseDTO.getErrors().get(0).getErrorMessage()
									: NULL_IDREPO_RESPONSE;
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							idResponseDTO != null ? idResponseDTO.getErrors().get(0).getErrorMessage()
									: NULL_IDREPO_RESPONSE + "  :  " + idResponseDTO != null ? idResponseDTO.toString()
											: NULL_IDREPO_RESPONSE);
				}
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString() + registrationId, "Response from IdRepo API",
						"is :" + idResponseDTO.toString());
			} else {
				if ((RegistrationType.ACTIVATED.toString()).equalsIgnoreCase(object.getReg_type())) {
					idResponseDTO = reActivateUin(registrationId, uinFieldCheck, object);
				} else if ((RegistrationType.DEACTIVATED.toString()).equalsIgnoreCase(object.getReg_type())) {
					idResponseDTO = deactivateUin(registrationId, uinFieldCheck, object);
				}
			}
			registrationStatusDto.setUpdatedBy(USER);
			registrationStatusService.updateRegistrationStatus(registrationStatusDto);
		} catch (FSAdapterException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId,
					PlatformErrorMessages.RPR_UGS_PACKET_STORE_NOT_ACCESSIBLE.getMessage() + e.getMessage());
			object.setInternalError(Boolean.TRUE);
			isTransactionSuccessful = false;
			description = "FileSytem is not accessible for packet " + registrationId + "::" + e.getMessage();
			object.setIsValid(Boolean.FALSE);
			object.setRid(registrationId);
		} catch (ApisResourceAccessException ex) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, RegistrationStatusCode.PACKET_UIN_UPDATION_SUCCESS.toString() + ex.getMessage()
					+ ExceptionUtils.getStackTrace(ex));
			object.setInternalError(Boolean.TRUE);
			description = "Internal error occured in UINGenerator stage while processing registrationId "
					+ registrationId + "::" + ex.getMessage();
		} catch (Exception ex) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, RegistrationStatusCode.PACKET_UIN_UPDATION_SUCCESS.toString() + ex.getMessage()
					+ ExceptionUtils.getStackTrace(ex));
			object.setInternalError(Boolean.TRUE);
			description = "Internal error occured in UINGenerator stage while processing registrationId "
					+ registrationId + ex.getMessage();
		} finally {

			String eventId = isTransactionSuccessful ? EventId.RPR_402.toString() : EventId.RPR_405.toString();
			String eventName = eventId.equalsIgnoreCase(EventId.RPR_402.toString()) ? EventName.UPDATE.toString()
					: EventName.EXCEPTION.toString();
			String eventType = eventId.equalsIgnoreCase(EventId.RPR_402.toString()) ? EventType.BUSINESS.toString()
					: EventType.SYSTEM.toString();
			auditLogRequestBuilder.createAuditRequestBuilder(description, eventId, eventName, eventType,
					registrationId, ApiName.AUDIT);

		}

		return object;
	}

	/**
	 * Send id repo with uin.
	 *
	 * @param regId
	 *            the reg id
	 * @param uin
	 *            the uin
	 * @return the id response DTO
	 * @throws ApisResourceAccessException
	 * @throws Exception
	 */
	private IdResponseDTO sendIdRepoWithUin(String regId, String uin) throws ApisResourceAccessException {

		List<Documents> documentInfo = getAllDocumentsByRegId(regId);
		RequestDto requestDto = new RequestDto();
		requestDto.setIdentity(demographicIdentity);
		requestDto.setDocuments(documentInfo);

		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(uin);
		IdResponseDTO result = null;
		idRequestDTO.setId(idRepoCreate);
		idRequestDTO.setRegistrationId(regId);
		idRequestDTO.setRequest(requestDto);
		idRequestDTO.setTimestamp(DateUtils.getUTCCurrentDateTimeString());
		idRequestDTO.setVersion(idRepoApiVersion);
		Gson gson = new GsonBuilder().create();
		String idRequest = gson.toJson(idRequestDTO);
		regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
				LoggerFileConstant.REGISTRATIONID.toString() + regId, "Request to IdRepo API", "is: " + idRequest);
		try {
			result = (IdResponseDTO) registrationProcessorRestClientService.postApi(ApiName.IDREPOSITORY, pathsegments,
					"", "", idRequestDTO, IdResponseDTO.class);
			regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
					LoggerFileConstant.REGISTRATIONID.toString() + regId, "Response from IdRepo API",
					"is : " + result.toString());

		} catch (ApisResourceAccessException e) {
			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				description = UIN_GENERATION_FAILED + registrationId + "::"
						+ httpClientException.getResponseBodyAsString();
				throw new ApisResourceAccessException(httpClientException.getResponseBodyAsString(),
						httpClientException);
			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				description = UIN_GENERATION_FAILED + registrationId + "::"
						+ httpServerException.getResponseBodyAsString();

				throw new ApisResourceAccessException(httpServerException.getResponseBodyAsString(),
						httpServerException);
			} else {
				description = UIN_GENERATION_FAILED + registrationId + "::" + e.getMessage();
				throw new ApisResourceAccessException(UIN_GENERATION_FAILED, e);
			}

		}
		return result;

	}

	/**
	 * Gets the all documents by reg id.
	 *
	 * @param regId
	 *            the reg id
	 * @return the all documents by reg id
	 */
	private List<Documents> getAllDocumentsByRegId(String regId) {
		List<Documents> applicantDocuments = new ArrayList<>();
		Documents documentsInfoDto = null;
		List<ApplicantDocument> applicantDocument = packetInfoManager.getDocumentsByRegId(regId);
		applicantDocuments.add(addBiometricDetails(regId));
		for (ApplicantDocument entity : applicantDocument) {
			documentsInfoDto = new Documents();
			documentsInfoDto.setCategory(entity.getDocName());
			documentsInfoDto.setValue(CryptoUtil.encodeBase64(entity.getDocStore()));
			applicantDocuments.add(documentsInfoDto);
		}
		return applicantDocuments;
	}

	/**
	 * Adds the biometric details.
	 *
	 * @param regId
	 *            the reg id
	 * @return the documents
	 */
	private Documents addBiometricDetails(String regId) {
		Documents document = new Documents();

		byte[] biometricDocument = getFile(regId);
		String getIdentityJsonString = Utilities.getJson(utility.getConfigServerFileStorageURL(),
				utility.getGetRegProcessorIdentityJson());
		ObjectMapper mapIdentityJsonStringToObject = new ObjectMapper();
		try {
			regProcessorIdentityJson = mapIdentityJsonStringToObject.readValue(getIdentityJsonString,
					RegistrationProcessorIdentity.class);
		} catch (IOException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getMessage() + e.getMessage());
		}
		String individuaBiometricValue = regProcessorIdentityJson.getIdentity().getIndividualBiometrics().getValue();
		document.setCategory(individuaBiometricValue);
		document.setValue(CryptoUtil.encodeBase64(biometricDocument));
		return document;
	}

	/**
	 * Gets the file.
	 *
	 * @param registrationId
	 *            the registration id
	 * @return the file
	 */
	private byte[] getFile(String registrationId) {
		byte[] file = null;
		InputStream packetMetaInfoStream = adapter.getFile(registrationId, PacketFiles.PACKET_META_INFO.name());
		PacketMetaInfo packetMetaInfo = null;
		String applicantBiometricFileName = "";
		try {
			packetMetaInfo = (PacketMetaInfo) JsonUtil.inputStreamtoJavaObject(packetMetaInfoStream,
					PacketMetaInfo.class);

			List<FieldValueArray> hashSequence = packetMetaInfo.getIdentity().getHashSequence1();
			List<String> hashList = identityIteratorUtil.getHashSequence(hashSequence,
					JsonConstant.APPLICANTBIOMETRICSEQUENCE);
			if (hashList != null)
				applicantBiometricFileName = hashList.get(0);
			InputStream fileInStream = adapter.getFile(registrationId,
					PacketStructure.BIOMETRIC + applicantBiometricFileName.toUpperCase());
			file = IOUtils.toByteArray(fileInStream);

		} catch (IOException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getMessage() + e.getMessage());
		}
		return file;
	}


	/**
	 * Re activate uin.
	 *
	 * @param regId the reg id
	 * @param uin the uin
	 * @param object the object
	 * @return the id response DTO
	 * @throws ApisResourceAccessException the apis resource access exception
	 */
	private IdResponseDTO reActivateUin(String regId, Long uin, MessageDTO object) throws ApisResourceAccessException {
		IdResponseDTO result = getIdRepoDataByUIN(uin);
		List<String> pathsegments = new ArrayList<>();

		try {
			if (result != null && result.getResponse() != null) {

				if ((RegistrationType.ACTIVATED.toString()).equalsIgnoreCase(result.getStatus())) {

					registrationStatusDto.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());
					registrationStatusDto.setStatusComment(
							UinStatusMessage.UIN_UPDATION_ALREADY_ACTIVATED + " for registration Id:  " + regId);
					description = UinStatusMessage.UIN_UPDATION_ALREADY_ACTIVATED + " for registration Id:  " + regId;
					object.setIsValid(Boolean.FALSE);
					return result;

				} else {

					pathsegments.add(Long.toString(uin));
					idRequestDTO.setId(idRepoUpdate);
					idRequestDTO.setRegistrationId(regId);
					idRequestDTO.setStatus(RegistrationType.ACTIVATED.toString());
					idRequestDTO.setTimestamp(DateUtils.getUTCCurrentDateTimeString());
					idRequestDTO.setVersion(idRepoApiVersion);
					Gson gson = new GsonBuilder().create();
					String idReq = gson.toJson(idResponseDTO);

					regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString() + regId, "Update Request to IdRepo API",
							"is : " + idReq);

					result = (IdResponseDTO) registrationProcessorRestClientService.patchApi(ApiName.IDREPOSITORY,
							pathsegments, "", "", idRequestDTO, IdResponseDTO.class);

					if (result != null && result.getResponse() != null) {

						if ((RegistrationType.ACTIVATED.toString()).equalsIgnoreCase(result.getStatus())) {
							isTransactionSuccessful = true;
							registrationStatusDto
									.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_SUCCESS.toString());
							registrationStatusDto.setStatusComment(
									UinStatusMessage.UIN_UPDATION_ACTIVATED + " for registration Id:  " + regId);
							description = UinStatusMessage.UIN_UPDATION_ACTIVATED + " for registration Id:  " + regId;
							object.setIsValid(Boolean.TRUE);
						} else {

							registrationStatusDto
									.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());
							registrationStatusDto.setStatusComment(UinStatusMessage.UIN_UPDATION_RE_ACTIVATION_FAILURE
									+ " for registration Id:  " + regId);
							description = UinStatusMessage.UIN_UPDATION_RE_ACTIVATION_FAILURE
									+ " for registration Id:  " + regId;
							object.setIsValid(Boolean.FALSE);
						}
					} else {
						String statusComment = result != null && result.getErrors() != null
								? result.getErrors().get(0).getErrorMessage()
								: NULL_IDREPO_RESPONSE;
						registrationStatusDto.setStatusComment(statusComment);
						registrationStatusDto
								.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());

						description = UIN_FAILURE + regId + "::" + result != null && result.getErrors() != null
								? result.getErrors().get(0).getErrorMessage()
								: NULL_IDREPO_RESPONSE;
						object.setIsValid(Boolean.FALSE);
					}

				}

			}
		} catch (ApisResourceAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION.getMessage() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
		}
		return result;
	}

	/**
	 * Deactivate uin.
	 *
	 * @param regId the reg id
	 * @param uin the uin
	 * @param object the object
	 * @return the id response DTO
	 */
	private IdResponseDTO deactivateUin(String regId, Long uin, MessageDTO object) {
		IdResponseDTO idResponseDto = new IdResponseDTO();
		List<String> pathsegments = new ArrayList<>();
		String statusComment ="";

		try {
			idResponseDto = getIdRepoDataByUIN(uin);

			if (idResponseDto.getResponse() != null && idResponseDto.getStatus().equalsIgnoreCase(RegistrationType.DEACTIVATED.toString())) {

				registrationStatusDto.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());
				registrationStatusDto.setStatusComment(UinStatusMessage.UIN_DEACTIVATE_FAILURE + regId);
				description = UinStatusMessage.UIN_DEACTIVATE_FAILURE + regId;
				object.setIsValid(Boolean.FALSE);
				return idResponseDto;

			} else {
				pathsegments.add(Long.toString(uin));
				idRequestDTO.setId(idRepoUpdate);
				idRequestDTO.setRegistrationId(regId);
				idRequestDTO.setStatus(RegistrationType.DEACTIVATED.toString());
				idRequestDTO.setTimestamp(DateUtils.getUTCCurrentDateTimeString());
				idRequestDTO.setVersion(idRepoApiVersion);

				idResponseDto = (IdResponseDTO) registrationProcessorRestClientService.patchApi(ApiName.IDREPOSITORY,
						pathsegments, "", "", idRequestDTO, IdResponseDTO.class);

				if (idResponseDto != null && idResponseDto.getResponse() != null) {
					if (idResponseDto.getStatus().equalsIgnoreCase(RegistrationType.DEACTIVATED.toString())) {
						registrationStatusDto
								.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_SUCCESS.toString());
						registrationStatusDto.setStatusComment(UinStatusMessage.UIN_DEACTIVATE_SUCCESS + regId);
						description = UinStatusMessage.UIN_DEACTIVATE_SUCCESS + regId;
						object.setIsValid(Boolean.TRUE);
						statusComment=idResponseDto.getStatus().toString();
	
					}
				} else {

					statusComment = idResponseDto != null && idResponseDto.getErrors() != null
							? idResponseDto.getErrors().get(0).getErrorMessage()
							: NULL_IDREPO_RESPONSE;
					registrationStatusDto.setStatusCode(RegistrationStatusCode.PACKET_UIN_UPDATION_FAILURE.toString());
					registrationStatusDto.setStatusComment(statusComment);
					description = statusComment;
					object.setIsValid(Boolean.FALSE);
				}

			}
			regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
					LoggerFileConstant.REGISTRATIONID.toString() + regId, "Updated Response from IdRepo API",
					"is : " + statusComment);
		} catch (ApisResourceAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION.getMessage() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
		}

		return idResponseDto;
	}


	/**
	 * Gets the id repo data by UIN.
	 *
	 * @param uin the uin
	 * @return the id repo data by UIN
	 * @throws ApisResourceAccessException the apis resource access exception
	 */
	private IdResponseDTO getIdRepoDataByUIN(Long uin) throws ApisResourceAccessException{
		IdResponseDTO response  = new IdResponseDTO();

		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(Long.toString(uin));
		try {
			response = (IdResponseDTO) registrationProcessorRestClientService.getApi(ApiName.IDREPOSITORY, pathsegments, "",
					"", IdResponseDTO.class);
		} catch (ApisResourceAccessException e) {
			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				description = UIN_GENERATION_FAILED + registrationId + "::"
						+ httpClientException.getResponseBodyAsString();
				throw new ApisResourceAccessException(httpClientException.getResponseBodyAsString(),
						httpClientException);
			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				description = UIN_GENERATION_FAILED + registrationId + "::"
						+ httpServerException.getResponseBodyAsString();

				throw new ApisResourceAccessException(httpServerException.getResponseBodyAsString(),
						httpServerException);
			} else {
				description = UIN_GENERATION_FAILED + registrationId + "::" + e.getMessage();
				throw new ApisResourceAccessException(UIN_GENERATION_FAILED, e);
			}
		}
		return response;
	}

	/**
	 * Deploy verticle.
	 */
	public void deployVerticle() {
		//
		mosipEventBus = this.getEventBus(this, clusterManagerUrl, 50);
		this.consumeAndSend(mosipEventBus, MessageBusAddress.UIN_GENERATION_BUS_IN,
				MessageBusAddress.UIN_GENERATION_BUS_OUT);

	}
}