/* Copyright (C) 2017 [Gobierno de Espana]
 * This file is part of FIRe.
 * FIRe is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 08/09/2017
 * You may contact the copyright holder at: soporte.afirma@correo.gob.es
 */
package es.gob.fire.server.services.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import es.gob.afirma.core.misc.LoggerUtil;
import es.gob.fire.server.services.Responser;
import es.gob.fire.server.services.internal.TempDocumentsManager;
import es.gob.fire.signature.ConfigManager;

/** Servicio de almacenamiento temporal de firmas. &Uacute;til para servir de intermediario en comunicaci&oacute;n
 * entre JavaScript y <i>Apps</i> m&oacute;viles nativas.
 * @author Tom&aacute;s Garc&iacute;a-;er&aacute;s */
public final class RetrieveService extends HttpServlet {

	private static final long serialVersionUID = -3272368448371213403L;

	/** Log para registrar las acciones del servicio. */
	private static final Logger LOGGER = Logger.getLogger(RetrieveService.class.getName());

	/** Nombre del par&aacute;metro con la operaci&oacute;n realizada. */
	private static final String PARAMETER_NAME_OPERATION = "op"; //$NON-NLS-1$

	/** Nombre del par&aacute;metro con el identificador del fichero temporal. */
	private static final String PARAMETER_NAME_ID = "id"; //$NON-NLS-1$

	/** Nombre del par&aacute;metro con la versi&oacute;n de la sintaxis de petici&oacute; utilizada. */
	private static final String PARAMETER_NAME_SYNTAX_VERSION = "v"; //$NON-NLS-1$

	private static final String OPERATION_RETRIEVE = "get"; //$NON-NLS-1$

	private static final boolean HIGH_AVAILABILITY_ENABLED;

	static {
		final String sessionsDao = ConfigManager.getSessionsDao();
		HIGH_AVAILABILITY_ENABLED = sessionsDao != null && !sessionsDao.trim().isEmpty();
	}

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) {

		LOGGER.fine("== INICIO DE LA RECUPERACION =="); //$NON-NLS-1$

		final String operation = request.getParameter(PARAMETER_NAME_OPERATION);
		final String syntaxVersion = request.getParameter(PARAMETER_NAME_SYNTAX_VERSION);
		final String id = request.getParameter(PARAMETER_NAME_ID);
		response.setHeader("Access-Control-Allow-Origin", "*"); //$NON-NLS-1$ //$NON-NLS-2$
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); //$NON-NLS-1$ //$NON-NLS-2$

		if (operation == null) {
			LOGGER.warning(ErrorManager.genError(ErrorManager.ERROR_MISSING_OPERATION_NAME));
			sendResult(response, ErrorManager.genError(ErrorManager.ERROR_MISSING_OPERATION_NAME));
			return;
		}
		if (syntaxVersion == null) {
			LOGGER.warning(ErrorManager.genError(ErrorManager.ERROR_MISSING_SYNTAX_VERSION));
			sendResult(response, ErrorManager.genError(ErrorManager.ERROR_MISSING_SYNTAX_VERSION));
			return;
		}
		if (id == null) {
			LOGGER.warning(ErrorManager.genError(ErrorManager.ERROR_MISSING_DATA_ID));
			sendResult(response, ErrorManager.genError(ErrorManager.ERROR_MISSING_DATA_ID));
			return;
		}
		if (!OPERATION_RETRIEVE.equalsIgnoreCase(operation)) {
			LOGGER.warning(ErrorManager.genError(ErrorManager.ERROR_UNSUPPORTED_OPERATION_NAME));
			sendResult(response, ErrorManager.genError(ErrorManager.ERROR_UNSUPPORTED_OPERATION_NAME));
			return;
		}

		retrieveSign(response, request, id);

		LOGGER.fine("== FIN DE LA RECUPERACION =="); //$NON-NLS-1$
	}

	/**
	 * Recupera la firma del servidor.
	 * @param response Respuesta a la petici&oacute;n.
	 * @param request Petici&oacute;n.
	 * @param id Identificador del documento a recuperar.
	 */
	private static void retrieveSign(final HttpServletResponse response,
			final HttpServletRequest request, final String id) {

		// Tratamos de cargar los datos de la cache en memoria
		byte[] data = ClienteAfirmaCache.recoverData(id);

		// Si no se encuentran los datos, comprobamos si estamos en modo alta disponibilidad
		// y se encuentran en el almacen comun. Si tampoco, se indica que no se
		// encuentran
		if (data == null) {

			boolean existsDocument = false;
			if (HIGH_AVAILABILITY_ENABLED) {
				try {
					existsDocument = TempDocumentsManager.existDocument(id);
				}
				catch (final Exception e) {
					LOGGER.log(Level.SEVERE, "Error al comprobar la existencia del documento " + LoggerUtil.getTrimStr(id), e); //$NON-NLS-1$
					existsDocument = false;
				}
			}

			if (existsDocument) {
				try {
					data = TempDocumentsManager.retrieveAndDeleteDocument(id);
				}
				catch (final Exception e) {
					LOGGER.log(Level.SEVERE, ErrorManager.genError(ErrorManager.ERROR_INVALID_DATA), e);
					sendResult(response, ErrorManager.genError(ErrorManager.ERROR_INVALID_DATA));
					return;
				}
			}
			else {
				sendResult(response, ErrorManager.genError(ErrorManager.ERROR_INVALID_DATA_ID)  + " ('" + LoggerUtil.getTrimStr(id) + "')"); //$NON-NLS-1$ //$NON-NLS-2$
				return;
			}
		}
		// Si hemos recuperado los datos de memoria, pero estamos en modo alta disponibilidad,
		// debemos eliminarlos del almacen comun para que no queden ahi residuales
		else if (HIGH_AVAILABILITY_ENABLED) {
			new DeleteSharedFileThread(id).start();
		}

		sendResult(response, data);
	}

    private static void sendResult(final HttpServletResponse response, final String text) {
    	response.setContentType("text/plain"); //$NON-NLS-1$
		response.setCharacterEncoding("utf-8"); //$NON-NLS-1$
		Responser.sendResult(response, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendResult(final HttpServletResponse response, final byte[] data) {
    	Responser.sendResult(response, data);
    }

    /**
     * Hilo para la eliminaci&oacute;n de datos en el almacen com&uacute;n.
     */
    static class DeleteSharedFileThread extends Thread {

    	private final String docId;

    	/**
    	 * Crea el objeto para la eliminaci&oacute;n de un documento del almac&eacute;n temporal.
    	 * @param docId Identificador del documento a eliminar.
    	 */
    	public DeleteSharedFileThread(final String docId) {
    		this.docId = docId;
		}

    	@Override
    	public void run() {
    		super.run();
    		try {
				TempDocumentsManager.deleteDocument(this.docId);
			} catch (final IOException e) {
				Logger.getLogger(RetrieveService.class.getName()).warning("No se pudo eliminar el fichero " //$NON-NLS-1$
						+ this.docId + " de comunicacion con el Cliente Afirma del almacen comun: " + e); //$NON-NLS-1$
			}
    	}
    }
}