/* Copyright (C) 2017 [Gobierno de Espana]
 * This file is part of FIRe.
 * FIRe is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 08/09/2017
 * You may contact the copyright holder at: soporte.afirma@correo.gob.es
 */
package es.gob.fire.server.services.internal;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import es.gob.fire.server.services.Responser;
import es.gob.fire.statistics.entity.Browser;

/**
 * Servlet que redirige a la p&aacute;gina de selecci&oacute;n del origen del certificado
 * de firma.
 */
public class ChooseOriginService extends HttpServlet {

	/** Serial ID. */
	private static final long serialVersionUID = -1923506369793382006L;

	private static final Logger LOGGER = Logger.getLogger(ChooseOriginService.class.getName());

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException {

		// Ya que este es uno de los puntos de entrada del usuario a la operativa de FIRe, se establece aqui
		// el tiempo maximo de sesion
		setSessionMaxAge(request);

		final String subjectRef = request.getParameter(ServiceParams.HTTP_PARAM_SUBJECT_REF);
		final String trId = request.getParameter(ServiceParams.HTTP_PARAM_TRANSACTION_ID);
		final String redirectErrorUrl = request.getParameter(ServiceParams.HTTP_PARAM_ERROR_URL);

		if (subjectRef == null || trId == null) {
			Responser.sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		final TransactionAuxParams trAux = new TransactionAuxParams(null, trId);
		final LogTransactionFormatter logF = trAux.getLogFormatter();

		// Cargamos los datos de sesion
		final FireSession session = SessionCollector.getFireSessionOfuscated(trId, subjectRef, request.getSession(false), false, false, trAux);
		if (session == null) {
			LOGGER.severe(logF.f("No existe sesion vigente asociada a la transaccion")); //$NON-NLS-1$
			Responser.redirectToExternalUrl(redirectErrorUrl, request, response, trAux);
			return;
		}

		// Identificamos el navegador para uso de las estadisticas
		final String userAgent = request.getHeader("user-agent"); //$NON-NLS-1$
		final Browser browser =  Browser.identify(userAgent);
		session.setAttribute(ServiceParams.SESSION_PARAM_BROWSER, browser.getName());

		// Registramos los datos guardados
		SessionCollector.commit(session, trAux);

    	Responser.redirectToUrl(FirePages.PG_CHOOSE_CERTIFICATE_ORIGIN, request, response, trAux);
	}

	/**
	 * Establece el tiempo maximo de vida de la sesi&oacute;n del usuario.
	 * @param request Petici&oacute;n realizada al servicio.
	 */
	private static void setSessionMaxAge(final HttpServletRequest request) {
		final HttpSession httpSession = request.getSession(false);
		if (httpSession != null) {
			httpSession.setMaxInactiveInterval((int) (FireSession.MAX_INACTIVE_INTERVAL / 1000));
		}
	}
}
