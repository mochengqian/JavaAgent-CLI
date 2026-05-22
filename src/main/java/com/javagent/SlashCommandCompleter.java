package com.javagent;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;

/**
 * JLine3 Completer for slash commands.
 * Shows a filtered list of commands as the user types.
 */
public class SlashCommandCompleter implements Completer {

    public record CommandDef(String name, String description) {}

    private final List<CommandDef> commands = new ArrayList<>();

    public void register(String name, String description) {
        commands.add(new CommandDef(name, description));
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line().trim();
        if (!buffer.startsWith("/")) return;

        for (CommandDef def : commands) {
            if (def.name().startsWith(buffer)) {
                String display = String.format("%-20s %s", def.name(), def.description());
                candidates.add(new Candidate(
                        def.name(), display, "commands", def.description(),
                        null, null, true
                ));
            }
        }
    }

    public List<CommandDef> allCommands() {
        return new ArrayList<>(commands);
    }
}
