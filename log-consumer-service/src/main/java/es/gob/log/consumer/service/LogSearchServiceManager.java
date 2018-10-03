package es.gob.log.consumer.service;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import es.gob.log.consumer.InvalidPatternException;
import es.gob.log.consumer.LogInfo;
import es.gob.log.consumer.LogReader;
import es.gob.log.consumer.LogSearchText;

public class LogSearchServiceManager {

	private static final Logger LOGGER = Logger.getLogger(LogTailServiceManager.class.getName());
	private static boolean hasMore = false;

	public final static byte[] process(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		byte[] result = null;
		/* Obtenemos los par&aacute;metros*/

		final String sNumLines = req.getParameter(ServiceParams.NUM_LINES);
		final String text = req.getParameter(ServiceParams.SEARCH_TEXT);
		final boolean reset = Boolean.parseBoolean(req.getParameter(ServiceParams.PARAM_RESET));
		Long sdateTime = null;
		if(req.getParameter(ServiceParams.SEARCH_DATETIME) != null && !"".equals(req.getParameter(ServiceParams.SEARCH_DATETIME))) { //$NON-NLS-1$
			sdateTime = new Long( Long.parseLong(req.getParameter(ServiceParams.SEARCH_DATETIME)));
		}
		final HttpSession session = req.getSession(true);
		final LogInfo info = (LogInfo)session.getAttribute("LogInfo"); //$NON-NLS-1$
		final LogReader reader = (LogReader)session.getAttribute("Reader"); //$NON-NLS-1$
		final Long fileSize = (Long) session.getAttribute("FileSize");  //$NON-NLS-1$
		final AsynchronousFileChannel channel = (AsynchronousFileChannel)session.getAttribute("Channel"); //$NON-NLS-1$
		Long filePosition =(Long)session.getAttribute("FilePosition");//$NON-NLS-1$


		try {

			if(reset) {
				reader.close();
				reader.load();
				//Reset de la posicion de sesion de tail
				if(filePosition != null && filePosition.longValue() > 0L) {
					filePosition = new Long(0L);
					session.setAttribute("FilePosition", filePosition); //$NON-NLS-1$
				}
			}

			if( channel.size() > fileSize.longValue() && reader.isEndFile()) {
				session.setAttribute("FileSize", new Long (channel.size())); //$NON-NLS-1$
				if(reader.getFilePosition() > 0L) {
					reader.reload(reader.getFilePosition());
				}
			}

			final LogSearchText logSearch = new LogSearchText(info);

			if(sdateTime == null || sdateTime.longValue() < 0L) {
				result = logSearch.searchText(Integer.parseInt(sNumLines), text, reader);//Integer.parseInt(sNumLines) - nlines.intValue()
			}
			else {
				result = logSearch.searchText(Integer.parseInt(sNumLines) , text, sdateTime.longValue(), reader);
			}


			session.setAttribute("Reader", reader); //$NON-NLS-1$

			if(result == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No se han encontrado m�s ocurrencias en la b�squeda"); //$NON-NLS-1$
				result = "No se han encontrado m�s ocurrencias en la b�squeda".getBytes( info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
			}

		} catch (final InvalidPatternException e) {
			LOGGER.log(Level.SEVERE,"El patr&oacute;n indicado con la forma de los registros del log, no es v&aacute;lido.",e.getMessage()); //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "El patr�n indicado con la forma de los registros del log, no es v�lido."); //$NON-NLS-1$
			result = "El patr�n indicado con la forma de los registros del log, no es v�lido.".getBytes( info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
			return result;
		} catch (final IOException e) {
			LOGGER.log(Level.SEVERE,"No se ha podido leer el fichero",e.getMessage()); //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No se ha podido leer el fichero"); //$NON-NLS-1$
			result = "No se ha podido leer el fichero".getBytes( info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
			return result;
		}
		catch (final InterruptedException e) {
			LOGGER.log(Level.SEVERE,"Error al procesar la petici&oacute;n buscar. ",e.getMessage()); //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Error al procesar la petici�n buscar."); //$NON-NLS-1$
			result ="Error al procesar la petici�n buscar.".getBytes(info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
		}
		catch (final ExecutionException e) {
			LOGGER.log(Level.SEVERE,"Error al procesar la petici&oacute;n buscar.",e.getMessage()); //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Error al procesar la petici�n buscar."); //$NON-NLS-1$
			result = "Error al procesar la petici�n buscar.".getBytes( info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
			return result;

		}
		catch (final Exception e) {
			LOGGER.log(Level.SEVERE,"No se ha podido leer el fichero",e.getMessage()); //$NON-NLS-1$
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Error al procesar la petici�n buscar."); //$NON-NLS-1$
			result = "Error al procesar la petici�n buscar.".getBytes( info != null ? info.getCharset() : StandardCharsets.UTF_8); //$NON-NLS-1$
			return result;
		}

		return result;
	}


	public static final boolean isHasMore() {
		return hasMore;
	}

	private static final void setHasMore(final boolean hasMore) {
		LogSearchServiceManager.hasMore = hasMore;
	}



}