/*
 * Copyright (c) 2002-2014, Mairie de Paris
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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.portal.service.mail.MailService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import net.fortuna.ical4j.model.DateTime;
//import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.Rsvp;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
//import java.util.TimeZone;

/**
 * Service to send iCal appointments by email
 */
public class ICalService {
	/**
	 * The name of the bean of this service
	 */
	public static final String BEAN_NAME = "workflow-appointment.iCalService";
	private static final String PROPERTY_MAIL_LIST_SEPARATOR = "mail.list.separator";
	private static final String PROPERTY_ICAL_PRODID = "workflow-appointment.ical.prodid";
	private static final String CONSTANT_MAILTO = "MAILTO:";

	/**
	 * Get an instance of the service
	 * 
	 * @return An instance of the bean of this service
	 */
	public static ICalService getService() {
		return SpringContextService.getBean(BEAN_NAME);
	}

	/**
	 * Send an appointment to a user by email.
	 * 
	 * @param strEmailAttendee
	 *            Comma separated list of users that will attend the appointment
	 * @param strEmailOptionnal
	 *            Comma separated list of users that will be invited to the
	 *            appointment, but who are not required.
	 * @param strSubject
	 *            The subject of the appointment.
	 * @param strBodyContent
	 *            The body content that describes the appointment
	 * @param strLocation
	 *            The location of the appointment
	 * @param strSenderName
	 *            The name of the sender
	 * @param strSenderEmail
	 *            The email of the sender
	 * @param appointment
	 *            The appointment
	 * @param bCreate
	 *            True to notify the creation of the appointment, false to
	 *            notify its removal
	 */
	public void sendAppointment(String strEmailAttendee, String strEmailOptionnal, String strSubject,
			String strBodyContent, String strLocation, String strSenderName, String strSenderEmail,
			Appointment appointment, boolean bCreate) {
		Slot slot = SlotService.findSlotById(appointment.getIdSlot());
		DateTime beginningDateTime = new DateTime(
				slot.getStartingDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		DateTime endingDateTime = new DateTime(
				slot.getEndingDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		VEvent event = new VEvent(beginningDateTime, endingDateTime,
				(strSubject != null) ? strSubject : StringUtils.EMPTY);
		try {
			event.getProperties().add(new Uid(Appointment.APPOINTMENT_RESOURCE_TYPE + appointment.getIdAppointment()));
			String strEmailSeparator = AppPropertiesService.getProperty(PROPERTY_MAIL_LIST_SEPARATOR, ";");
			if (StringUtils.isNotEmpty(strEmailAttendee)) {
				StringTokenizer st = new StringTokenizer(strEmailAttendee, strEmailSeparator);
				while (st.hasMoreTokens()) {
					addAttendee(event, st.nextToken(), true);
				}
			}
			if (StringUtils.isNotEmpty(strEmailOptionnal)) {
				StringTokenizer st = new StringTokenizer(strEmailOptionnal, strEmailSeparator);
				while (st.hasMoreTokens()) {
					addAttendee(event, st.nextToken(), false);
				}
			}
			Organizer organizer = new Organizer(strSenderEmail);
			organizer.getParameters().add(new Cn(strSenderName));
			event.getProperties().add(organizer);
			event.getProperties().add(new Location(strLocation));
			event.getProperties().add(new Description(strBodyContent));
		} catch (URISyntaxException e) {
			AppLogService.error(e.getMessage(), e);
		}
		net.fortuna.ical4j.model.Calendar iCalendar = new net.fortuna.ical4j.model.Calendar();
		iCalendar.getProperties().add(bCreate ? Method.REQUEST : Method.CANCEL);
		iCalendar.getProperties().add(new ProdId(AppPropertiesService.getProperty(PROPERTY_ICAL_PRODID)));
		iCalendar.getProperties().add(Version.VERSION_2_0);
		iCalendar.getProperties().add(CalScale.GREGORIAN);
		iCalendar.getComponents().add(event);
		MailService.sendMailCalendar(strEmailAttendee, strEmailOptionnal, null, strSenderName, strSenderEmail,
				(strSubject != null) ? strSubject : StringUtils.EMPTY, strBodyContent, iCalendar.toString(), bCreate);
	}

	/**
	 * Add an attendee to an event
	 * 
	 * @param event
	 *            The event to add the attendee to
	 * @param strEmail
	 *            The email of the user
	 * @param bRequired
	 *            True if the presence of the user is mandatory, false if it is
	 *            optional
	 */
	private void addAttendee(VEvent event, String strEmail, boolean bRequired) {
		Attendee attendee = new Attendee(URI.create(CONSTANT_MAILTO + strEmail));
		attendee.getParameters().add(bRequired ? Role.REQ_PARTICIPANT : Role.OPT_PARTICIPANT);
		attendee.getParameters().add(PartStat.NEEDS_ACTION);
		attendee.getParameters().add(Rsvp.FALSE);
		event.getProperties().add(attendee);
	}

}
