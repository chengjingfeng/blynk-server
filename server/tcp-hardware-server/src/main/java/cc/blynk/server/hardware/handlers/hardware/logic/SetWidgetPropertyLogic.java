package cc.blynk.server.hardware.handlers.hardware.logic;

import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.utils.ParseUtil;
import cc.blynk.utils.ReflectionUtil;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.protocol.enums.Command.SET_WIDGET_PROPERTY;
import static cc.blynk.server.core.protocol.enums.Response.ILLEGAL_COMMAND_BODY;
import static cc.blynk.utils.ByteBufUtil.makeResponse;
import static cc.blynk.utils.ByteBufUtil.ok;
import static cc.blynk.utils.StringUtils.split3;

/**
 * Handler that allows to change widget properties from hardware side.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class SetWidgetPropertyLogic {

    private static final Logger log = LogManager.getLogger(SetWidgetPropertyLogic.class);

    private final SessionDao sessionDao;

    public SetWidgetPropertyLogic(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    public void messageReceived(ChannelHandlerContext ctx, HardwareStateHolder state, StringMessage message) {
        Session session = sessionDao.userSession.get(state.userKey);

        String[] bodyParts = split3(message.body);

        if (bodyParts.length != 3) {
            log.error("SetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(makeResponse(message.id, ILLEGAL_COMMAND_BODY), ctx.voidPromise());
            return;
        }

        byte pin = ParseUtil.parseByte(bodyParts[0]);
        String property = bodyParts[1];
        String propertyValue = bodyParts[2];

        if (property.length() == 0 || propertyValue.length() == 0) {
            log.error("SetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(makeResponse(message.id, ILLEGAL_COMMAND_BODY), ctx.voidPromise());
            return;
        }

        DashBoard dash = state.user.profile.getDashByIdOrThrow(state.dashId);

        //todo add test for this use case
        if (!dash.isActive) {
            return;
        }

        //for now supporting only virtual pins
        Widget widget = dash.findWidgetByPin(pin, PinType.VIRTUAL);

        if (widget == null) {
            log.error("No widget for SetWidgetProperty command. {}", message.body);
            ctx.writeAndFlush(makeResponse(message.id, ILLEGAL_COMMAND_BODY), ctx.voidPromise());
            return;
        }

        boolean isChanged;
        try {
            isChanged = ReflectionUtil.setProperty(widget, property, propertyValue);
        } catch (Exception e) {
            log.error("Error setting widget property. Reason : {}", e.getMessage());
            ctx.writeAndFlush(makeResponse(message.id, ILLEGAL_COMMAND_BODY), ctx.voidPromise());
            return;
        }

        if (isChanged) {
            session.sendToApps(SET_WIDGET_PROPERTY, message.id, dash.id + StringUtils.BODY_SEPARATOR_STRING + message.body);

            ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
        } else {
            ctx.writeAndFlush(makeResponse(message.id, ILLEGAL_COMMAND_BODY), ctx.voidPromise());
        }
    }

}
