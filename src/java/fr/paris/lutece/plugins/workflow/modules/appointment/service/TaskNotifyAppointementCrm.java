package fr.paris.lutece.plugins.workflow.modules.appointment.service;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;

import fr.paris.lutece.plugins.appointment.business.Appointment;
import fr.paris.lutece.plugins.appointment.business.AppointmentHome;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.crmclient.service.ICRMClientService;
import fr.paris.lutece.plugins.crmclient.util.CRMException;
import fr.paris.lutece.plugins.crmclient.util.CrmClientConstants;
import fr.paris.lutece.plugins.workflow.modules.appointment.business.TaskNotifyCrmConfig;
import fr.paris.lutece.plugins.workflowcore.business.resource.ResourceHistory;
import fr.paris.lutece.plugins.workflowcore.service.config.ITaskConfigService;
import fr.paris.lutece.plugins.workflowcore.service.resource.IResourceHistoryService;
import fr.paris.lutece.plugins.workflowcore.service.task.SimpleTask;
import fr.paris.lutece.portal.service.i18n.I18nService;

public class TaskNotifyAppointementCrm extends SimpleTask{

	   /**
     * Name of the bean of the config service of this task
     */
    public static final String CONFIG_SERVICE_BEAN_NAME = "workflow-appointment.taskNotifyCrmConfigService";
    
    
    private static final String MARK_FIRSTNAME = "${firstName}";
    private static final String MARK_LASTNAME = "${lastName}";
    private static final String MARK_EMAIL = "${email}";
    private static final String MARK_REFERENCE = "${reference}";
    private static final String MARK_DATE_APPOINTMENT = "${date_appointment}";
    private static final String MARK_TIME_APPOINTMENT = "${time_appointment}";
  
    // SERVICES
    @Inject
    private IResourceHistoryService _resourceHistoryService;
    @Inject
    private ICRMClientService _crmClientService;
    
    @Inject
    @Named( CONFIG_SERVICE_BEAN_NAME )
    private ITaskConfigService _taskNotifyAppointmentCrmConfigService;
    @Override
	public void processTask(int nIdResourceHistory, HttpServletRequest request,
			Locale locale) {
    	
    	ResourceHistory resourceHistory = _resourceHistoryService.findByPrimaryKey( nIdResourceHistory );
    	TaskNotifyCrmConfig config = _taskNotifyAppointmentCrmConfigService.findByPrimaryKey( this.getId(  ) );
    	Appointment appointment = AppointmentHome.findByPrimaryKey( resourceHistory.getIdResource(  ) );
    	String   strIdDemand =null;
    	
    	if(config != null ){
	    	
	    	try {
				strIdDemand = _crmClientService.sendCreateDemandByUserGuid( config.getDemandeType(), appointment.getIdUser( ),
						config.getIdStatusCRM(), config.getStatusText(), config.getData() );
			} catch (CRMException e) {
				e.printStackTrace();
			}
    	}
    	if(strIdDemand != null){
    		
    		String mesg= getMessageAppointment(config.getMessage(), appointment);
    		_crmClientService.notify( strIdDemand, config.getObject(), mesg, config.getSender() );
    		
    	}
    	
		
	}

	@Override
	public String getTitle(Locale locale) {
		
		TaskNotifyCrmConfig config=_taskNotifyAppointmentCrmConfigService.findByPrimaryKey(this.getId(  ));
		
		if ( config != null )
	        {
	            return config.getDemandeType();
	        }

	        return StringUtils.EMPTY;
		
	}
	
	 /**
     * {@inheritDoc}
     */
    @Override
    public void doRemoveConfig(  )
    {
    	_taskNotifyAppointmentCrmConfigService.remove( this.getId(  ) );
    }
    
    private String getMessageAppointment(String msg, Appointment appointment){
    	
    	String message = (msg.replace(MARK_FIRSTNAME, appointment.getFirstName())).replace(MARK_LASTNAME, appointment.getLastName());
    	String m=message.replace(MARK_REFERENCE, AppointmentService.getService(  ).computeRefAppointment( appointment ));
    	String messag = m.replace(MARK_DATE_APPOINTMENT, appointment.getDateAppointment().toString());
    	
    	
    	return messag;
    }

}