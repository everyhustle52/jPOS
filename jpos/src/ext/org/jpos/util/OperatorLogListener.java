/*
 * Copyright (c) 2000 jPOS.org.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *    "This product includes software developed by the jPOS project 
 *    (http://www.jpos.org/)". Alternately, this acknowledgment may 
 *    appear in the software itself, if and wherever such third-party 
 *    acknowledgments normally appear.
 *
 * 4. The names "jPOS" and "jPOS.org" must not be used to endorse 
 *    or promote products derived from this software without prior 
 *    written permission. For written permission, please contact 
 *    license@jpos.org.
 *
 * 5. Products derived from this software may not be called "jPOS",
 *    nor may "jPOS" appear in their name, without prior written
 *    permission of the jPOS project.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  
 * IN NO EVENT SHALL THE JPOS PROJECT OR ITS CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS 
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the jPOS Project.  For more
 * information please see <http://www.jpos.org/>.
 */

/*
 * $Log$
 * Revision 1.9  2003/09/03 00:22:14  apr
 * New LogListener interface returns [possibly null] LogEvent
 *
 * Revision 1.8  2003/05/21 17:57:34  apr
 * Supports multiple destinations:
 *
 *   "jpos.operator.to"
 *   "jpos.operator.cc"
 *   "jpos.operator.bcc"
 *
 * Revision 1.7  2003/05/16 07:14:30  alwyns
 * Import cleanups. Should work as expected now.
 *
 * Revision 1.6  2002/12/04 11:34:12  apr
 * Oops: missing import
 *
 * Revision 1.5  2002/12/04 01:32:43  apr
 * Different filenames in multiparts.
 * End filenames with ".txt" so dumb mailers that doesn't
 * understand text/plain mimetype give you a chance to open the
 * attachment without saving it to disk.
 *
 * Revision 1.4  2000/11/02 12:09:17  apr
 * Added license to every source file
 *
 * Revision 1.3  2000/05/25 23:34:13  apr
 * Implements Configurable (used by QSP)
 *
 * Revision 1.2  2000/03/01 14:44:45  apr
 * Changed package name to org.jpos
 *
 * Revision 1.1  2000/01/23 16:04:48  apr
 * Added OperatorLogListener
 *
 */

package org.jpos.util;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.jpos.iso.ISOUtil;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;

/**
 * send e-mail with selected LogEvents to operator account
 * <b>Configuration properties</b>
 * <pre>
 *    jpos.operator.from=jpos
 *    jpos.operator.to=operator@foo.bar
 *    jpos.operator.subject.prefix=[jPOS]
 *    jpos.operator.tags="Operator ISORequest SystemMonitor"
 *    jpos.operator.delay=10000
 *    jpos.mail.smtp.host=localhost
 * </pre>
 *
 * @author apr@cs.com.uy
 * @version $Id$
 */
public class OperatorLogListener 
    implements LogListener, Configurable, Runnable
{
    Configuration cfg;
    BlockingQueue queue;

    public OperatorLogListener () {
	super();
	queue = new BlockingQueue();
    }
    public OperatorLogListener (Configuration cfg) {
	super();
	this.cfg = cfg;
	queue = new BlockingQueue();
	new Thread(this).start();
    }
    public void setConfiguration (Configuration cfg) 
	throws ConfigurationException
    {
	this.cfg = cfg;
	assertProperty ("jpos.operator.to");
	assertProperty ("jpos.operator.subject.prefix");
	assertProperty ("jpos.operator.tags");
	assertProperty ("jpos.operator.delay");
	assertProperty ("jpos.mail.smtp.host");
	new Thread(this).start();
    }
    public void run() {
	Thread.currentThread().setName ("OperatorLogListener");
	int delay = cfg.getInt ("jpos.operator.delay");
	try {
            ISOUtil.sleep (2500);   // initial delay
	    for (;;) {
		try {
		    LogEvent ev[] = new LogEvent[1];
		    if (queue.pending() > 0) {
			ev = new LogEvent [queue.pending()];
			for (int i=0; i < ev.length; i++)
			    ev[i] = (LogEvent) queue.dequeue();
		    } else 
			ev[0] = (LogEvent) queue.dequeue();
		    sendMail (ev);
		    if (delay > 0)
			Thread.sleep (delay);
		} catch (InterruptedException e) { }
	    }
	} catch (BlockingQueue.Closed e) { }
    }
    private void sendMail (LogEvent[] ev) {
	String from    = cfg.get ("jpos.operator.from", "jpos-logger");
	String[] to    = cfg.getAll ("jpos.operator.to");
	String[] cc    = cfg.getAll ("jpos.operator.cc");
	String[] bcc   = cfg.getAll ("jpos.operator.bcc");
	String subject = cfg.get ("jpos.operator.subject.prefix");
	if (ev.length > 1) 
	    subject = subject + ev.length + " events";
	else
	    subject = subject + ev[0].getRealm() + " - " +ev[0].tag;

	// create some properties and get the default Session
	Properties props = System.getProperties();
	props.put("mail.smtp.host", cfg.get ("jpos.mail.smtp.host", 
		"localhost"));
	
	Session session = Session.getDefaultInstance(props, null);
	session.setDebug(false);
	
	try {
	    // create a message
	    MimeMessage msg = new MimeMessage(session);
	    msg.setFrom (new InternetAddress(from));

	    InternetAddress[] address = new InternetAddress[to.length];
            for (int i=0; i<to.length; i++) 
                address[i] = new InternetAddress (to[i]);
	    msg.setRecipients (Message.RecipientType.TO, getAddress (to));
	    msg.setRecipients (Message.RecipientType.CC, getAddress (cc));
	    msg.setRecipients (Message.RecipientType.BCC, getAddress (bcc));
	    msg.setSubject(subject);
	    Multipart mp = new MimeMultipart();

	    for(int i=0; i<ev.length; i++) {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		PrintStream p = new PrintStream (buf);
		ev[i].dump (p, "");
		p.close();
	
		// create and fill the first message part
		MimeBodyPart mbp = new MimeBodyPart();
		mbp.setText(buf.toString());
		mbp.setFileName (ev[i].tag + "_" + i + ".txt");
		mp.addBodyPart(mbp);
	    }
	    msg.setContent(mp);
	    msg.setSentDate(new Date());
	    Transport.send(msg);
	} catch (MessagingException mex) {
	    mex.printStackTrace();
	    Exception ex = null;
	    if ((ex = mex.getNextException()) != null) {
		ex.printStackTrace();
	    }
	}
    }
    private boolean checkOperatorTag(LogEvent ev) {
	String tags = cfg.get ("jpos.operator.tags");
	return tags.indexOf (ev.tag) >= 0;
    }
    private InternetAddress[] getAddress (String[] s) throws AddressException {
        InternetAddress[] address = new InternetAddress[s.length];
        for (int i=0; i<s.length; i++) 
            address[i] = new InternetAddress (s[i]);
        return address;
    }
    public synchronized LogEvent log (LogEvent ev) {
	if (checkOperatorTag(ev))
	    queue.enqueue (ev);
        return ev;
    }
    private void assertProperty (String propName) throws ConfigurationException
    {
	if (cfg.get (propName) == null)
	    throw new ConfigurationException 
		(propName + " property not present");
    }
}
