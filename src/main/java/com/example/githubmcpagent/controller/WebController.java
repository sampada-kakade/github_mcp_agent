package com.example.githubmcpagent.controller;

import com.example.githubmcpagent.service.DockerService;
import com.example.githubmcpagent.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebController {

    @Autowired
    private DockerService dockerService;

    @Autowired
    private OpenAIService openAIService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("repo", "Shubhamsaboo/awesome-llm-apps");
        return "index";
    }

    @PostMapping("/run")
    public String runQuery(
            @RequestParam(required = false) String openaiKey,
            @RequestParam(required = false) String githubToken,
            @RequestParam String repo,
            @RequestParam String query,
            Model model) {
        if ((githubToken == null || githubToken.isBlank()) && System.getenv("GITHUB_TOKEN") == null) {
            model.addAttribute("error", "GitHub token not provided. Set GITHUB_TOKEN or provide it in the form.");
            return "index";
        }

        if ((openaiKey == null || openaiKey.isBlank()) && System.getenv("OPENAI_API_KEY") == null) {
            model.addAttribute("error", "OpenAI API key not provided. Set OPENAI_API_KEY or provide it in the form.");
            return "index";
        }

        if (query == null || query.isBlank()) {
            model.addAttribute("error", "Query text is required.");
            return "index";
        }

        String effectiveGithub = (githubToken == null || githubToken.isBlank()) ? System.getenv("GITHUB_TOKEN")
                : githubToken;
        String effectiveOpenAI = (openaiKey == null || openaiKey.isBlank()) ? System.getenv("OPENAI_API_KEY")
                : openaiKey;

        String fullQuery = query;
        if (repo != null && !repo.isBlank() && !query.contains(repo)) {
            fullQuery = query + " in " + repo;
        }

        String aiInterpretation = openAIService.generateText(fullQuery, effectiveOpenAI);
        String dockerOutput = dockerService.runMcpDocker(effectiveGithub, effectiveOpenAI, fullQuery);
        String combined = "AI Interpretation:\n" + aiInterpretation + "\n\nMCP Docker Output:\n" + dockerOutput;
        model.addAttribute("result", combined);
        return "result";
    }
}
