package com.stremioshell.host.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stremioshell.host.compose.navigation.AppScreen
import com.stremioshell.host.compose.theme.StremioOverlay
import com.stremioshell.host.compose.theme.StremioPlaceholder
import com.stremioshell.host.compose.theme.StremioPlaceholderText
import com.stremioshell.host.compose.theme.StremioPrimaryAccent
import com.stremioshell.host.compose.theme.StremioPrimaryForeground
import com.stremioshell.host.compose.theme.StremioSecondaryAccent
import com.stremioshell.host.compose.theme.StremioSurface

private val NavRailWidth = 62.dp
private val TopBarHeight = 58.dp
private val WarningBarHeight = 76.dp
private val PillShape = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(8.dp)
private val FocusBorder = BorderStroke(width = 1.5.dp, color = StremioPrimaryForeground)

private val DefaultNavTabs = listOf(
  NavTabSpec(AppScreen.Board, Icons.Outlined.Home),
  NavTabSpec(AppScreen.Discover, Icons.Outlined.TravelExplore),
  NavTabSpec(AppScreen.Library, Icons.Outlined.LiveTv),
  NavTabSpec(AppScreen.Calendar, Icons.Outlined.CalendarMonth),
  NavTabSpec(AppScreen.Addons, Icons.Outlined.Extension),
  NavTabSpec(AppScreen.Settings, Icons.Outlined.Settings)
)

data class NavTabSpec(
  val screen: AppScreen,
  val icon: ImageVector
)

@Composable
fun MainNavBars(
  currentScreen: AppScreen,
  searchQuery: String,
  onSearchQueryChanged: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onNavigate: (AppScreen) -> Unit,
  focusRequesters: Map<String, FocusRequester>,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .width(NavRailWidth)
        .fillMaxHeight()
        .padding(top = 10.dp, bottom = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .background(
            brush = Brush.linearGradient(
              colors = listOf(
                StremioPrimaryAccent,
                Color(0xFF2B8AF7)
              )
            ),
            shape = RoundedCornerShape(8.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = ">",
          color = StremioPrimaryForeground,
          style = MaterialTheme.typography.titleMedium
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      DefaultNavTabs.forEach { tab ->
        NavTabButton(
          icon = tab.icon,
          selected = currentScreen == tab.screen,
          onClick = { onNavigate(tab.screen) },
          modifier = Modifier
            .focusRequester(focusRequesters.getValue(tab.screen.route))
            .size(48.dp)
        )
      }
    }

    Row(
      modifier = Modifier
        .padding(start = NavRailWidth + 8.dp, end = 10.dp, top = 10.dp)
        .height(TopBarHeight - 8.dp)
        .fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      SearchBar(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        onSubmit = onSubmitSearch,
        modifier = Modifier.width(360.dp)
      )

      Spacer(modifier = Modifier.weight(1f))

      UtilityButton(icon = Icons.Outlined.Search, onClick = onSubmitSearch)
      UtilityButton(icon = Icons.Outlined.AccountCircle, onClick = {})
    }
  }
}

@Composable
fun SearchBar(
  value: String,
  onValueChange: (String) -> Unit,
  onSubmit: () -> Unit,
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyLarge,
    placeholder = {
      Text(
        text = "Search or paste link",
        color = StremioPrimaryForeground.copy(alpha = 0.6f)
      )
    },
    trailingIcon = {
      UtilityButton(icon = Icons.Outlined.Search, onClick = onSubmit)
    },
    modifier = modifier
      .height(36.dp)
      .background(color = StremioOverlay, shape = PillShape),
    shape = PillShape
  )
}

@Composable
fun NavTabButton(
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val iconColor = when {
    selected -> StremioPrimaryAccent
    focused -> StremioPrimaryForeground
    else -> StremioPrimaryForeground.copy(alpha = 0.35f)
  }

  Box(
    modifier = modifier
      .background(
        color = if (focused) StremioOverlay else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
      )
      .then(
        if (focused) Modifier.border(FocusBorder, RoundedCornerShape(8.dp)) else Modifier
      )
      .clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .focusable(interactionSource = interaction),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconColor,
      modifier = Modifier.size(22.dp)
    )
  }
}

@Composable
fun UtilityButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Box(
    modifier = modifier
      .size(30.dp)
      .background(
        color = if (focused) StremioOverlay else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
      )
      .then(
        if (focused) Modifier.border(FocusBorder, RoundedCornerShape(8.dp)) else Modifier
      )
      .clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .focusable(interactionSource = interaction),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = StremioPrimaryForeground.copy(alpha = if (focused) 0.95f else 0.7f),
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
fun MetaRow(
  title: String,
  items: List<String>,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.displaySmall,
      color = StremioPrimaryForeground.copy(alpha = 0.6f)
    )

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(horizontal = 1.dp)
    ) {
      items(items) { item ->
        MetaCard(title = item, onClick = { onSelect(item) })
      }
    }
  }
}

@Composable
fun MetaRowPlaceholder(title: String, count: Int = 5, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.displaySmall,
      color = StremioPrimaryForeground.copy(alpha = 0.45f)
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      items(count) { index ->
        MetaCard(title = "placeholder-$index", placeholder = true, onClick = {})
      }
    }
  }
}

@Composable
fun MetaCard(
  title: String,
  placeholder: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Column(
    modifier = modifier
      .width(112.dp)
      .clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .focusable(interactionSource = interaction),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier
        .height(182.dp)
        .fillMaxWidth()
        .background(
          brush = Brush.verticalGradient(
            colors = if (placeholder) {
              listOf(StremioPlaceholder, StremioPlaceholder)
            } else {
              listOf(
                StremioOverlay,
                StremioSurface
              )
            }
          ),
          shape = CardShape
        )
        .then(
          if (focused) Modifier.border(FocusBorder, CardShape) else Modifier
        )
    )

    Box(
      modifier = Modifier
        .padding(horizontal = 6.dp)
        .height(12.dp)
        .fillMaxWidth(0.78f)
        .background(
          color = if (placeholder) StremioPlaceholder else StremioOverlay,
          shape = CircleShape
        )
    )

    if (!placeholder) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = StremioPrimaryForeground.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 5.dp)
      )
    }
  }
}

@Composable
fun UpdateWarningBanner(
  message: String,
  onInstall: () -> Unit,
  onLater: () -> Unit,
  onDontShowAgain: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(WarningBarHeight)
      .background(color = Color(0xFFC88D00), shape = RoundedCornerShape(10.dp))
      .padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.headlineMedium,
      color = StremioPrimaryForeground,
      modifier = Modifier.weight(1f)
    )

    BannerAction(label = "Install", onClick = onInstall)
    BannerAction(label = "Later", onClick = onLater)
    BannerAction(label = "Don't show again", onClick = onDontShowAgain)
  }
}

@Composable
private fun BannerAction(label: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Box(
    modifier = Modifier
      .background(
        color = if (focused) Color(0xFFA57700) else Color(0xAA8F6700),
        shape = RoundedCornerShape(16.dp)
      )
      .then(
        if (focused) Modifier.border(FocusBorder, RoundedCornerShape(16.dp)) else Modifier
      )
      .clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .focusable(interactionSource = interaction)
      .padding(horizontal = 18.dp, vertical = 10.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      color = StremioPrimaryForeground
    )
  }
}

@Composable
fun EmptyRouteHint(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(18.dp)
      .background(StremioOverlay, RoundedCornerShape(10.dp))
      .padding(14.dp)
  ) {
    Text(
      text = text,
      color = StremioPlaceholderText,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

fun contentPaddings(showStreamingWarning: Boolean): PaddingValues {
  return PaddingValues(
    start = NavRailWidth + 10.dp,
    top = TopBarHeight + 6.dp,
    end = 10.dp,
    bottom = if (showStreamingWarning) WarningBarHeight + 10.dp else 10.dp
  )
}
