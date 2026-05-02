package com.tabariyya;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.tabariyya.utils.BaseLogger;

import java.util.Map;

@Component
public class WebLogger extends BaseLogger {

    @Override
    protected String createLogMessage(String message) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String method = request.getMethod();
        String path = request.getRequestURI();

        Map<String, Object> logData = super.prepareLogData(message);
        logData.put("method", method);
        logData.put("path", path);
        return convertMapToJson(logData);
    }
}
