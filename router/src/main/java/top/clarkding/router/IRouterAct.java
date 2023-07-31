package top.clarkding.router;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public interface IRouterAct {

    public void putActivity(Map<String, Class<? extends AppCompatActivity>> routes);
}
