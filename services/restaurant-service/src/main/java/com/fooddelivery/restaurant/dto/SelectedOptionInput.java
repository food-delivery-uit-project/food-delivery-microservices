package com.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SelectedOptionInput(
    @JsonAlias({"optionName", "option_name"})
    String optionName,

    @JsonAlias({"choiceName", "choice_name", "choiceLabel", "choice_label"})
    String choiceName
) {}
