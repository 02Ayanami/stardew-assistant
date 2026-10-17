package dev.stardew.assistant.data;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "assistant.import", havingValue = "true")
public class ImportCommand implements ApplicationRunner {
  private final DataImportService importer;
  private final String path;
  private final ConfigurableApplicationContext context;

  public ImportCommand(
      DataImportService importer,
      @Value("${assistant.data-dir}") String path,
      ConfigurableApplicationContext context) {
    this.importer = importer;
    this.path = path;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    boolean changed = importer.importData(Path.of(path));
    System.out.println(changed ? "资料导入完成" : "资料和索引未改变，已跳过");
    SpringApplication.exit(context, () -> 0);
  }
}
