/*
 * Copyright (c) 2002-2022, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.workflow.modules.appointment.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.user.User;
import fr.paris.lutece.plugins.appointment.service.AppointmentResponseService;
import fr.paris.lutece.plugins.appointment.service.UserService;
import fr.paris.lutece.plugins.appointment.service.entrytype.EntryTypePhone;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.appointment.web.dto.ResponseRecapDTO;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryFilter;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.business.ResponseHome;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.workflow.modules.appointment.business.EmailDTO;
import fr.paris.lutece.plugins.workflow.modules.appointment.business.NotifyAppointmentDTO;
import fr.paris.lutece.plugins.workflow.modules.appointment.provider.AppointmentNotificationMarkers;
import fr.paris.lutece.plugins.workflow.modules.appointment.provider.AppointmentWorkflowConstants;
import fr.paris.lutece.plugins.workflowcore.business.resource.ResourceHistory;
import fr.paris.lutece.plugins.workflowcore.service.provider.InfoMarker;
import fr.paris.lutece.plugins.workflowcore.service.task.SimpleTask;
import fr.paris.lutece.portal.service.mail.MailService;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.html.HtmlTemplate;
import fr.paris.lutece.util.string.StringUtil;

/**
 * Abstract task to notify a user of an appointment.
 * 
 * @param <T>
 *            The type of the DTO to use to send the email
 */
public abstract class AbstractTaskNotifyAppointment<T extends NotifyAppointmentDTO> extends SimpleTask
{
    /**
     * Property that contains the address of the SMS server
     */
    private static final String PROPERTY_SMS_SERVER = "workflow-appointment.sms.server";

    // TEMPLATES
    private static final String TEMPLATE_TASK_NOTIFY_MAIL = "admin/plugins/workflow/modules/appointment/task_notify_appointment_mail.html";
    private static final String TEMPLATE_TASK_NOTIFY_SMS = "admin/plugins/workflow/modules/appointment/task_notify_appointment_sms.html";
    private static final String TEMPLATE_TASK_NOTIFY_APPOINTMENT_RECAP = "admin/plugins/workflow/modules/appointment/task_notify_appointment_recap.html";

    private ICalService _iCalService;

    /**
     * Send an email to a user
     * 
     * @param appointment
     *            The appointment
     * @param resourceHistory
     *            The resource history
     * @param request
     *            The request
     * @param locale
     *            The locale
     * @param notifyAppointmentDTO
     *            The DTO with data of the email
     * @param strEmail
     *            The address to send the email to
     * @return The content sent, or null if no email was sent
     */
    public EmailDTO sendEmail( AppointmentDTO appointment, ResourceHistory resourceHistory, HttpServletRequest request, Locale locale, T notifyAppointmentDTO,
            String strEmail )
    {
        if ( notifyAppointmentDTO == null || resourceHistory == null || appointment == null
                || !Appointment.APPOINTMENT_RESOURCE_TYPE.equals( resourceHistory.getResourceType( ) ) )
        {
            return null;
        }

        if ( StringUtils.isEmpty( notifyAppointmentDTO.getSenderEmail( ) ) || !StringUtil.checkEmail( notifyAppointmentDTO.getSenderEmail( ) ) )
        {
            notifyAppointmentDTO.setSenderEmail( MailService.getNoReplyEmail( ) );
        }
        if ( StringUtils.isBlank( notifyAppointmentDTO.getSenderName( ) ) )
        {
            notifyAppointmentDTO.setSenderName( notifyAppointmentDTO.getSenderEmail( ) );
        }
        Map<String, Object> model = fillModel( request, notifyAppointmentDTO, appointment, locale );
        String strSubject = AppTemplateService.getTemplateFromStringFtl( notifyAppointmentDTO.getSubject( ), locale, model ).getHtml( );
        boolean bHasRecipients = ( StringUtils.isNotBlank( notifyAppointmentDTO.getRecipientsBcc( ) )
                || StringUtils.isNotBlank( notifyAppointmentDTO.getRecipientsCc( ) ) );
        String strContent = AppTemplateService.getTemplateFromStringFtl( AppTemplateService
                .getTemplate( notifyAppointmentDTO.getIsSms( ) ? TEMPLATE_TASK_NOTIFY_SMS : TEMPLATE_TASK_NOTIFY_MAIL, locale, model ).getHtml( ), locale,
                model ).getHtml( );
        if ( notifyAppointmentDTO.getSendICalNotif( ) )
        {
            getICalService( ).sendAppointment( strEmail, notifyAppointmentDTO.getRecipientsCc( ), strSubject, strContent, notifyAppointmentDTO.getLocation( ),
                    notifyAppointmentDTO.getSenderName( ), notifyAppointmentDTO.getSenderEmail( ), appointment, notifyAppointmentDTO.getCreateNotif( ) );
        }
        else
        {
            if ( bHasRecipients )
            {
                MailService.sendMailHtml( strEmail, notifyAppointmentDTO.getRecipientsCc( ), notifyAppointmentDTO.getRecipientsBcc( ),
                        notifyAppointmentDTO.getSenderName( ), notifyAppointmentDTO.getSenderEmail( ), strSubject, strContent );
            }
            else
            {
                MailService.sendMailHtml( strEmail, notifyAppointmentDTO.getSenderName( ), notifyAppointmentDTO.getSenderEmail( ), strSubject, strContent );
            }
        }
        return new EmailDTO( strSubject, strContent );
    }

    /**
     * Get a model to generate email content for a given appointment and a given task
     * 
     * @param request
     *            The request
     * @param notifyAppointmentDTO
     *            The configuration of the task
     * @param appointment
     *            The appointment to process
     * @param locale
     *            The locale
     * @return The model filled with data
     */
    public Map<String, Object> fillModel( HttpServletRequest request, T notifyAppointmentDTO, AppointmentDTO appointment, Locale locale )
    {
        // Create the Object providing the notification's markers
        AppointmentNotificationMarkers notificationMarkers = new AppointmentNotificationMarkers( appointment, notifyAppointmentDTO );

        // Get the Collection of available markers and their values
        Collection<InfoMarker> collectionNotifyMarkers = notificationMarkers.getMarkerValues( );

        // Retrieve a List of the appointment's Entry Response values
        List<ResponseRecapDTO> listResponseRecapDTO = getAppointmentResponseList( request, appointment ); 

        // Create a summary (recap) containing the appointment's responses
        // then add it to the existing markers collection
        String appointmentRecap = buildAppointmentRecap( listResponseRecapDTO, locale );
        AppointmentNotificationMarkers.addAppointmentRecap( collectionNotifyMarkers, appointmentRecap );

        // Return the markers and their values as the model used to fill an HTML template
        return markersToModel( collectionNotifyMarkers );
    }

    /**
     * Get the email address to use to send an SMS to the user of an appointment
     * 
     * @param appointment
     *            The appointment
     * @return The email address, or null if no phone number was found.
     */
    protected String getEmailForSmsFromAppointment( AppointmentDTO appointment )
    {
        String strPhoneNumber = null;
        EntryFilter entryFilter = new EntryFilter( );
        entryFilter.setIdResource( appointment.getIdForm( ) );
        entryFilter.setResourceType( AppointmentFormDTO.RESOURCE_TYPE );
        entryFilter.setFieldDependNull( EntryFilter.FILTER_TRUE );
        List<Integer> listIdResponse = AppointmentResponseService.findListIdResponse( appointment.getIdAppointment( ) );

        List<Response> listResponses = listIdResponse.stream( ).map( ResponseHome::findByPrimaryKey ).collect( Collectors.toList( ) );
        List<Entry> listEntries = EntryHome.getEntryList( entryFilter );
        for ( Entry entry : listEntries )
        {
            IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( entry );
            if ( entryTypeService instanceof EntryTypePhone )
            {
                for ( Response response : listResponses )
                {
                    if ( ( response.getEntry( ).getIdEntry( ) == entry.getIdEntry( ) ) && StringUtils.isNotBlank( response.getResponseValue( ) ) )
                    {
                        strPhoneNumber = response.getResponseValue( );
                        break;
                    }
                }
                if ( StringUtils.isNotEmpty( strPhoneNumber ) )
                {
                    break;
                }
            }
        }
        if ( StringUtils.isNotBlank( strPhoneNumber ) )
        {
            strPhoneNumber = strPhoneNumber + AppPropertiesService.getProperty( PROPERTY_SMS_SERVER );
        }
        return strPhoneNumber;
    }

    /**
     * Get the ICal service
     * 
     * @return The ICal service
     */
    private ICalService getICalService( )
    {
        if ( _iCalService == null )
        {
            _iCalService = ICalService.getService( );
        }
        return _iCalService;
    }

    /**
     * Get an appointment's List of summarized responses
     * 
     * @param request
     *            The request
     * @param appointment
     *            The appointment to process
     * @return the given appointment's List of ResponseRecapDTO
     */
    private List<ResponseRecapDTO> getAppointmentResponseList( HttpServletRequest request, AppointmentDTO appointment )
    {
        List<Response> listResponse = AppointmentResponseService.findListResponse( appointment.getIdAppointment( ) );
        List<ResponseRecapDTO> listResponseRecapDTO = new ArrayList<>( listResponse.size( ) );
        for ( Response response : listResponse )
        {
            IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( response.getEntry( ) );
            listResponseRecapDTO.add( new ResponseRecapDTO( response,
                    entryTypeService.getResponseValueForRecap( response.getEntry( ), request, response, request.getLocale( ) ) ) );
        }
        return listResponseRecapDTO;
    }

    /**
     * Create an appointment's responses summary (recap) by inserting their values in a template
     * 
     * @param listResponseRecapDTO
     *            The List of ResponseRecapDTO to display in the summary
     * @param locale
     *            The locale
     * @return an organized summary containing the given ResponseRecapDTO elements
     */
    private String buildAppointmentRecap( List<ResponseRecapDTO> listResponseRecapDTO, Locale locale )
    {
        Map<String, Object> model = new HashMap<>( );
        model.put( AppointmentWorkflowConstants.MARK_LIST_RESPONSE, listResponseRecapDTO );
        HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_TASK_NOTIFY_APPOINTMENT_RECAP, locale, model );

        return template.getHtml( );
    }

    /**
     * Converts the specified collection of Appointment Notification markers into a <key,value> Map
     * 
     * @param collectionMarkers
     *            The collection to convert
     * @return the Map containing the markers and their values
     */
    private Map<String, Object> markersToModel( Collection<InfoMarker> collectionMarkers )
    {
        Map<String, Object> model = new HashMap<>( );

        for ( InfoMarker marker : collectionMarkers )
        {
            model.put( marker.getMarker( ), marker.getValue( ) );
        }
        return model;
    }
}
