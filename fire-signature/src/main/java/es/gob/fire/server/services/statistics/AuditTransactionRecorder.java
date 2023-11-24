package es.gob.fire.server.services.statistics;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import es.gob.fire.logs.handlers.DailyFileHandler;
import es.gob.fire.server.services.internal.FireSession;
import es.gob.fire.server.services.internal.ServiceParams;
import es.gob.fire.signature.DBConnectionException;
import es.gob.fire.statistics.dao.AuditTransactionsDAO;
import es.gob.fire.statistics.entity.AuditTransactionCube;

public class AuditTransactionRecorder {

	private static final Logger LOGGER = Logger.getLogger(AuditTransactionRecorder.class.getName());

	private static String LOGGER_NAME = "AUDIT_TRANSACTION"; //$NON-NLS-1$

	private static String LOG_FILENAME = "FIRE_" + LOGGER_NAME + ".log"; //$NON-NLS-1$ //$NON-NLS-2$

	private static String LOG_CHARSET = "utf-8"; //$NON-NLS-1$

	private Logger dataLogger = null;

	private boolean enable;

	private boolean enableDB;

	private static AuditTransactionRecorder instance;

	/**
	 * Obtenemos el logger para el guardado de los datos de auditor&iacute;a de las peticiones realizadas.
	 * @return Objeto para el registro de los datos de las peticiones.
	 */
	public final static AuditTransactionRecorder getInstance() {
		if (instance == null){
			instance = new AuditTransactionRecorder();
		}
		return instance;
	}

	private AuditTransactionRecorder() {
		final AuditConfig config;
		try {
			config = AuditConfig.load();
		} catch (final Exception e) {
			LOGGER.warning("No se configuro una politica valida para el guardado de auditoria. No se almacenaran"); //$NON-NLS-1$
			return;
		}

		this.enable = config.isEnabled();
		this.enableDB = config.isSavingToDB();

		final String logsPath = config.getDataDirPath();
		if (logsPath == null || logsPath.isEmpty()) {
			LOGGER.warning("No se configuro un directorio para el guardado de auditoria. No se almacenaran"); //$NON-NLS-1$
			this.enable = false;
			return;
		}

		// Comprobamos que el directorio exista y se pueda escribir en el
		final File logsDir = new File(logsPath);
		if (!logsDir.isDirectory() || !logsDir.canWrite()) {
			LOGGER.log(Level.WARNING, "El directorio para el guardado de auditoria no existe o no se tienen permisos"); //$NON-NLS-1$
			this.enable = false;
			return;
		}

		LOGGER.fine("Se registraran los datos de las auditoria de transaccion"); //$NON-NLS-1$

		// Creamos el logger con el que imprimiremos los resultados a disco
		final Logger fileLogger = Logger.getLogger(LOGGER_NAME);
		fileLogger.setLevel(Level.FINEST);

		// Eliminamos todos los manejadores existentes
		for (final Handler handler : fileLogger.getHandlers()) {
			fileLogger.removeHandler(handler);
		}

		// Instalamos el manejador para la impresion en el fichero
		Handler logHandler = null;
		try {
			logHandler = new DailyFileHandler(new File(logsPath, LOG_FILENAME).getAbsolutePath());
			logHandler.setEncoding(LOG_CHARSET);
			logHandler.setFormatter(new Formatter() {
				@Override
				public String format(final LogRecord record) {
					return record.getMessage() + "\r\n"; //$NON-NLS-1$
				}
			});

			fileLogger.addHandler(logHandler);
		}
		catch (final Exception e) {
			LOGGER.log(Level.WARNING, "No se ha podido crear el fichero para la auditoria de transacciones", e); //$NON-NLS-1$
			this.enable = false;
			return;
		}
		

		this.dataLogger = fileLogger;
	}

	/**
	 * Registra los datos de la petici&oacute;n.
	 * @param fireSession Sesi&oacute;n con la informaci&oacute;n de la firma a realizar.
	 * @param result Resultado de la operaci&oacute;n ({@code true}, la firma termino
	 * correctamente o lo hizo alguna de las firmas del lote; {@code false}, no se pudo
	 * generar la firma o fallaron todas las firmas del lote).
	 * @param docId Identificador del documento firmado en caso de encontrarse dentro de un lote.
	 */
	public final void register(final FireSession fireSession, final boolean result) {

		register(fireSession, result, fireSession.getString(ServiceParams.SESSION_PARAM_ERROR_MESSAGE));
	}

	private String nodeName = null;

	/**
	 * Registra los datos de la petici&oacute;n.
	 * @param fireSession Sesi&oacute;n con la informaci&oacute;n de la firma a realizar.
	 * @param result Resultado de la operaci&oacute;n ({@code true}, la firma termino
	 * correctamente o lo hizo alguna de las firmas del lote; {@code false}, no se pudo
	 * generar la firma o fallaron todas las firmas del lote).
	 * @param docId Identificador del documento firmado en caso de encontrarse dentro de un lote.
	 * @param errorMessage Mensaje de error a almacenar. Si no se indica, se
	 * usar&aacute; el por defecto del tipo de error.
	 */
	public final void register(final FireSession fireSession, final boolean result, final String errorMessage) {

		// Si esta desactivado el registro, no se hace nada
		if (!this.enable) {
			return;
		}

		// Inicializamos el cubo de datos si no lo estaba
		final AuditTransactionCube auditTransactionCube = new AuditTransactionCube();

		// Fecha
		auditTransactionCube.setDate(new Date());

		// Id transaccion
		final String trId = fireSession.getString(ServiceParams.SESSION_PARAM_TRANSACTION_ID);
		auditTransactionCube.setIdTransaction(trId != null && !trId.isEmpty() ? trId : "0"); //$NON-NLS-1$

		// Resultado
		auditTransactionCube.setResult(result);

		// Id Aplicacion
		final String appId = fireSession.getString(ServiceParams.SESSION_PARAM_APPLICATION_ID);
		auditTransactionCube.setIdApplication(appId);

		// Nombre Aplicacion
		final String appName = fireSession.getString(ServiceParams.SESSION_PARAM_APPLICATION_NAME);
		auditTransactionCube.setNameApplication(appName);

		// Operacion
		TransactionType type = (TransactionType) fireSession.getObject(ServiceParams.SESSION_PARAM_TRANSACTION_TYPE);
		if (type == null) {
			type = TransactionType.OTHER;
		}
		auditTransactionCube.setOperation(type.name());

		// Operacion criptografica
		auditTransactionCube.setCryptoOperation(fireSession.getString(ServiceParams.SESSION_PARAM_CRYPTO_OPERATION));

		//Formato y formato mejorado

		// Obtenemos el tamano de la transaccion
		long docSize = 0;
		final Object docSizeObject = fireSession.getObject(ServiceParams.SESSION_PARAM_TRANSACTION_SIZE);
		if (docSizeObject != null && docSizeObject instanceof Long) {
			docSize = ((Long) docSizeObject).longValue();
		}

		// Obtenemos el formato de firma configurado
		final String format = fireSession.getString(ServiceParams.SESSION_PARAM_FORMAT);

		// Obtenemos el formato de actualizacion configurado
		final String upgrade = fireSession.getString(ServiceParams.SESSION_PARAM_UPGRADE);

		// Registramos el tamano del documento, el formato y el formato de actualizacion
		auditTransactionCube.setDataSize(docSize);
		auditTransactionCube.setFormat(format);
		auditTransactionCube.setImprovedFormat(upgrade);

		// Algoritmo
		final String algorithm = fireSession.getString(ServiceParams.SESSION_PARAM_ALGORITHM);
		auditTransactionCube.setAlgorithm(algorithm);

		// Almacenamos la informacion del proveedor
		final String[] provsSession = (String []) fireSession.getObject(ServiceParams.SESSION_PARAM_PROVIDERS);
		final String prov = fireSession.getString(ServiceParams.SESSION_PARAM_CERT_ORIGIN);
		final String provForced = fireSession.getString(ServiceParams.SESSION_PARAM_CERT_ORIGIN_FORCED);

		if (provForced != null && !provForced.isEmpty()) {
			auditTransactionCube.setProvider(provForced);
			auditTransactionCube.setMandatoryProvider(true);
		}
		else if (prov != null && !prov.isEmpty()) {
			auditTransactionCube.setProvider(prov);
		}
		else if(provsSession != null && provsSession.length == 1) {
			auditTransactionCube.setProvider(provsSession[0]);
			auditTransactionCube.setMandatoryProvider(true);
		}

		// Navegador
		final String browser = fireSession.getString(ServiceParams.SESSION_PARAM_BROWSER);
		auditTransactionCube.setBrowser(browser);

		// Resultado
		auditTransactionCube.setResult(result);

		//Error detalle
		final String errorDetail = errorMessage;
		if (errorDetail != null && !result) {
			auditTransactionCube.setErrorDetail(errorDetail);
		} else {
			auditTransactionCube.setErrorDetail(null);
		}

		// Nodo
		if (this.nodeName == null) {
			try {
				this.nodeName = InetAddress.getLocalHost().getHostName();
			} catch (final UnknownHostException e) {
				this.nodeName = "Desconocido"; //$NON-NLS-1$
			}
		}
		auditTransactionCube.setNode(this.nodeName);

		// Registramos el cubo en fichero
		this.dataLogger.finest(auditTransactionCube.toString());

		// Registramos el cubo en base de datos
		if (this.enableDB) {
			AuditTransactionsDAO.insertAuditTransaction(auditTransactionCube);
		}
	}

	public Logger getDataLogger() {
		return this.dataLogger;
	}
}