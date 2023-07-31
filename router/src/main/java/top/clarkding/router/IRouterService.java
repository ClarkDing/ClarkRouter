package top.clarkding.router;

import android.app.Service;

import java.util.Map;

public interface IRouterService {

    public void putActivity(Map<String, Class<? extends Service>> routes);
}
