package es.us.isa.botica.configuration.broker;

public class RabbitMqConfiguration implements BrokerConfiguration {
  private String username;
  private String password;
  private int port;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return "RabbitMqConfiguration{"
        + "username='"
        + username
        + '\''
        + ", password='"
        + password
        + '\''
        + ", port="
        + port
        + '}';
  }
}
