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

import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.workflow.modules.appointment.business.TaskNotifyAdminAppointmentConfig;
import fr.paris.lutece.plugins.workflow.modules.appointment.business.TaskNotifyAppointmentConfig;
import fr.paris.lutece.plugins.workflow.modules.appointment.provider.AppointmentWorkflowConstants;
import fr.paris.lutece.plugins.workflow.modules.appointment.web.ExecuteWorkflowAction;
import fr.paris.lutece.plugins.workflowcore.business.resource.ResourceHistory;
import fr.paris.lutece.plugins.workflowcore.service.config.ITaskConfigService;
import fr.paris.lutece.plugins.workflowcore.service.resource.IResourceHistoryService;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.business.user.AdminUserHome;
import fr.paris.lutece.portal.service.util.AppPathService;

/**
 * Workflow task to notify an admin user associated to an appointment. <br />
 * The admin user is the admin user specified in the configuration of the task, or the admin user associated with the appointment if no admin user is associated
 * to the configuration.
 */
public class TaskNotifyAdminAppointment extends AbstractTaskNotifyAppointment<TaskNotifyAdminAppointmentConfig>
{
    /**
     * Name of the bean of the config service of this task
     */
    public static final String CONFIG_SERVICE_BEAN_NAME = "workflow-appointment.taskNotifyAdminAppointmentConfigService";

    // SERVICES
    @Inject
    private IResourceHistoryService _resourceHistoryService;
    @Inject
    @Named( CONFIG_SERVICE_BEAN_NAME )
    private ITaskConfigService _taskNotifyAppointmentAdminConfigService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void processTask( int nIdResourceHistory, HttpServletRequest request, Locale locale )
    {
        TaskNotifyAdminAppointmentConfig config = _taskNotifyAppointmentAdminConfigService.findByPrimaryKey( this.getId( ) );
        if ( config != null )
        {
            ResourceHistory resourceHistory = _resourceHistoryService.findByPrimaryKey( nIdResourceHistory );
            if ( resourceHistory != null )
            {
                AppointmentDTO appointment = AppointmentService.buildAppointmentDTOFromIdAppointment( resourceHistory.getIdResource( ) );
                if ( appointment != null )
                {
                    AdminUser adminUser = null;
                    if ( config.getIdAdminUser( ) > 0 )
                    {
                        adminUser = AdminUserHome.findByPrimaryKey( config.getIdAdminUser( ) );
                    }
                    else
                    {
                        adminUser = AdminUserHome.findByPrimaryKey( appointment.getIdAdminUser( ) );
                    }
                    if ( adminUser != null )
                    {
                        this.sendEmail( appointment, resourceHistory, request, locale, config, adminUser.getEmail( ) );
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doRemoveConfig( )
    {
        _taskNotifyAppointmentAdminConfigService.remove( this.getId( ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTitle( Locale locale )
    {
        TaskNotifyAppointmentConfig config = _taskNotifyAppointmentAdminConfigService.findByPrimaryKey( this.getId( ) );

        if ( config != null )
        {
            return config.getSubject( );
        }

        return StringUtils.EMPTY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> fillModel( HttpServletRequest request, TaskNotifyAdminAppointmentConfig notifyAppointmentDTO, AppointmentDTO appointment,
            Locale locale )
    {
        // Get the <key, value> model containing a standard appointment's data (user's first name, last name, e-mail, date...)
        Map<String, Object> model = super.fillModel( request, notifyAppointmentDTO, appointment, locale );
        // Add values specific to the admin in the model
        addAdminValuesToModel( request, model, notifyAppointmentDTO, appointment );

        return model;
    }

    /**
     * Add specific values to the model, when the recipient of the notification is an administrator
     * 
     * @param request
     *            The request
     * @param model
     *            The model to fill with extra values
     * @param notifyAppointmentDTO
     *            The configuration of the task
     * @param appointment
     *            The appointment to get data from
     */
    private void addAdminValuesToModel( HttpServletRequest request, Map<String, Object> model, TaskNotifyAdminAppointmentConfig notifyAppointmentDTO,
            AppointmentDTO appointment )
    {
        // Add a URL to cancel the appointment through a Workflow Action
        model.put( AppointmentWorkflowConstants.MARK_URL_CANCEL, ExecuteWorkflowAction.getExecuteWorkflowActionUrl( AppPathService.getBaseUrl( request ),
                notifyAppointmentDTO.getIdActionCancel( ), notifyAppointmentDTO.getIdAdminUser( ), appointment.getIdAppointment( ) ) );
        // Add a URL to validate the appointment through a Workflow Action
        model.put( AppointmentWorkflowConstants.MARK_URL_VALIDATE, ExecuteWorkflowAction.getExecuteWorkflowActionUrl( AppPathService.getBaseUrl( request ),
                notifyAppointmentDTO.getIdActionValidate( ), notifyAppointmentDTO.getIdAdminUser( ), appointment.getIdAppointment( ) ) );
    }
}
