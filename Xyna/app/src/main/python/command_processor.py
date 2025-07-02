import re
from datetime import datetime, timedelta
import json

def parse_time(time_str):
    """Parse time strings into datetime objects."""
    now = datetime.now()
    
    # Handle relative time
    if 'in' in time_str:
        amount = re.findall(r'\d+', time_str)[0]
        if 'minute' in time_str:
            return now + timedelta(minutes=int(amount))
        elif 'hour' in time_str:
            return now + timedelta(hours=int(amount))
        elif 'day' in time_str:
            return now + timedelta(days=int(amount))
    
    # Handle specific time
    if 'am' in time_str.lower() or 'pm' in time_str.lower():
        return datetime.strptime(f"{now.strftime('%Y-%m-%d')} {time_str}", "%Y-%m-%d %I:%M %p")
    
    # Handle 24-hour format
    if ':' in time_str:
        return datetime.strptime(f"{now.strftime('%Y-%m-%d')} {time_str}", "%Y-%m-%d %H:%M")
    
    return None

def parse_duration(duration_str):
    """Parse duration strings into minutes."""
    match = re.search(r'(\d+)\s*(minute|hour|day)s?', duration_str.lower())
    if match:
        amount = int(match.group(1))
        unit = match.group(2)
        if unit == 'minute':
            return amount
        elif unit == 'hour':
            return amount * 60
        elif unit == 'day':
            return amount * 24 * 60
    return None

def extract_context(command):
    """Extract contextual information from the command."""
    context = {
        'time_context': None,
        'location_context': None,
        'app_context': None,
        'action_sequence': [],
        'conditions': []
    }
    
    # Time context
    time_patterns = ['at', 'after', 'before', 'when', 'during']
    for pattern in time_patterns:
        if pattern in command:
            parts = command.split(pattern)
            if len(parts) > 1:
                context['time_context'] = parts[1].split()[0]
    
    # Location context
    location_patterns = ['in', 'at', 'near', 'to']
    for pattern in location_patterns:
        if pattern in command:
            parts = command.split(pattern)
            if len(parts) > 1:
                context['location_context'] = parts[1].split()[0]
    
    # App context
    apps = ['youtube', 'whatsapp', 'instagram', 'facebook', 'twitter']
    for app in apps:
        if app in command:
            context['app_context'] = app
    
    # Action sequence
    if ' and ' in command:
        context['action_sequence'] = [action.strip() for action in command.split(' and ')]
    elif ' then ' in command:
        context['action_sequence'] = [action.strip() for action in command.split(' then ')]
    
    # Conditions
    if ' if ' in command:
        condition_parts = command.split(' if ')
        if len(condition_parts) > 1:
            context['conditions'].append(condition_parts[1])
    
    return context

def process_command(command):
    """Process user command and return action details."""
    command = command.lower().strip()
    
    # Environment analysis commands
    if any(phrase in command for phrase in ["see environment", "analyze surroundings", "look around", "what do you see"]):
        use_front_camera = "front" in command or "selfie" in command
        return {
            "action": "analyze_environment",
            "use_front_camera": use_front_camera
        }
    
    # Extract context first
    context = extract_context(command)
    
    # Handle complex multi-step tasks
    if context['action_sequence']:
        steps = []
        for action in context['action_sequence']:
            step = process_single_command(action, context)
            if step:
                steps.append(step)
        return {
            'action': 'sequence',
            'steps': steps,
            'context': context
        }
    
    # Handle conditional tasks
    if context['conditions']:
        main_action = process_single_command(command.split(' if ')[0], context)
        return {
            'action': 'conditional',
            'main_action': main_action,
            'condition': context['conditions'][0],
            'context': context
        }
    
    # Handle single commands
    return process_single_command(command, context)

def process_single_command(command, context=None):
    """Process a single command with context awareness."""
    command = command.lower().strip()
    
    # YouTube Commands with enhanced understanding
    if any(word in command for word in ['youtube', 'video', 'watch']):
        if 'search' in command or 'find' in command:
            query = extract_search_query(command)
            return {
                'action': 'youtube_search',
                'query': query,
                'context': context
            }
        elif 'play' in command:
            content = extract_content(command)
            return {
                'action': 'youtube_play',
                'video_id': content,
                'context': context
            }
    
    # WhatsApp Commands with smart contact handling
    if 'whatsapp' in command:
        if 'message' in command or 'send' in command:
            contact, message = extract_message_details(command)
            return {
                'action': 'whatsapp_message',
                'contact': contact,
                'message': message,
                'context': context
            }
        elif 'open' in command:
            return {
                'action': 'launch_app',
                'app': 'whatsapp',
                'context': context
            }
    
    # Social Media Commands with profile handling
    for platform in ['instagram', 'facebook', 'twitter']:
        if platform in command:
            if 'profile' in command:
                username = extract_username(command)
                return {
                    'action': f'{platform}_profile',
                    'username': username,
                    'context': context
                }
            elif 'open' in command:
                return {
                    'action': 'launch_app',
                    'app': platform,
                    'context': context
                }
    
    # Smart App Navigation
    if context and context['app_context']:
        action = extract_action(command)
        target = extract_target(command)
        return {
            'action': 'app_navigation',
            'app': context['app_context'],
            'navigation_action': action,
            'target': target,
            'context': context
        }
    
    # Communication Commands
    if match := re.match(r'(call|dial|phone)\s+(.+)', command):
        contact = match.group(2)
        return {
            'action': 'make_call',
            'contact': contact
        }
    
    if match := re.match(r'(message|text|send message to)\s+(.+?)\s+saying\s+(.+)', command):
        contact = match.group(2)
        message = match.group(3)
        return {
            'action': 'send_message',
            'contact': contact,
            'message': message
        }
    
    if match := re.match(r'(email|send email to)\s+(.+?)\s+about\s+(.+?)\s+saying\s+(.+)', command):
        recipient = match.group(2)
        subject = match.group(3)
        body = match.group(4)
        return {
            'action': 'send_email',
            'recipient': recipient,
            'subject': subject,
            'body': body
        }
    
    # Calendar & Reminders
    if match := re.match(r'remind me to\s+(.+?)\s+(at|in)\s+(.+)', command):
        task = match.group(1)
        time_str = match.group(3)
        time = parse_time(time_str)
        return {
            'action': 'create_reminder',
            'task': task,
            'time': time.isoformat() if time else None
        }
    
    if match := re.match(r'schedule\s+(.+?)\s+for\s+(.+?)\s+for\s+(.+)', command):
        event = match.group(1)
        time_str = match.group(2)
        duration_str = match.group(3)
        time = parse_time(time_str)
        duration = parse_duration(duration_str)
        return {
            'action': 'create_calendar_event',
            'event': event,
            'start_time': time.isoformat() if time else None,
            'duration_minutes': duration
        }
    
    # Device Control
    if 'wifi' in command:
        enable = 'on' in command or 'enable' in command
        return {
            'action': 'toggle_wifi',
            'enable': enable
        }
    
    if 'bluetooth' in command:
        enable = 'on' in command or 'enable' in command
        return {
            'action': 'toggle_bluetooth',
            'enable': enable
        }
    
    if match := re.match(r'set (volume|brightness) to (\d+)(?:percent)?', command):
        setting = match.group(1)
        value = int(match.group(2))
        return {
            'action': f'set_{setting}',
            'value': value
        }
    
    # App Control
    if match := re.match(r'open\s+(.+)', command):
        app = match.group(1)
        return {
            'action': 'launch_app',
            'app': app
        }
    
    if match := re.match(r'search( for)?\s+(.+)', command):
        query = match.group(2)
        return {
            'action': 'web_search',
            'query': query
        }
    
    # Navigation
    if match := re.match(r'(navigate|directions) to\s+(.+)', command):
        location = match.group(2)
        return {
            'action': 'navigate',
            'location': location
        }
    
    # Media Control
    if any(word in command for word in ['play', 'pause', 'next', 'previous', 'stop']):
        action = next(word for word in ['play', 'pause', 'next', 'previous', 'stop'] if word in command)
        return {
            'action': f'media_{action}'
        }
    
    # Device Information
    if 'battery' in command:
        return {
            'action': 'get_battery_info'
        }
    
    return {
        'action': 'unknown_command',
        'original_command': command
    }

def extract_search_query(command):
    """Extract search query from command."""
    search_markers = ['search for', 'search', 'find', 'look for']
    for marker in search_markers:
        if marker in command:
            query = command.split(marker)[-1]
            # Clean up the query
            query = query.replace('on youtube', '').replace('in youtube', '').strip()
            return query
    return command

def extract_content(command):
    """Extract content identifier from command."""
    content_markers = ['play', 'watch', 'start']
    for marker in content_markers:
        if marker in command:
            content = command.split(marker)[-1]
            # Clean up the content
            content = content.replace('on youtube', '').replace('video', '').strip()
            return content
    return command

def extract_message_details(command):
    """Extract contact and message from messaging command."""
    contact = None
    message = None
    
    # Try to find contact
    contact_markers = ['to', 'for', 'with']
    for marker in contact_markers:
        if marker in command:
            parts = command.split(marker)
            if len(parts) > 1:
                contact = parts[1].split('saying')[0].strip()
                break
    
    # Try to find message
    if 'saying' in command:
        message = command.split('saying')[-1].strip()
    
    return contact, message

def extract_username(command):
    """Extract username from social media command."""
    username_markers = ['profile', 'account', 'user']
    for marker in username_markers:
        if marker in command:
            username = command.split(marker)[-1].strip()
            return username
    return command.split()[-1]

def extract_action(command):
    """Extract action from command."""
    action_words = ['search', 'find', 'open', 'go to', 'show', 'display']
    for action in action_words:
        if action in command:
            return action
    return 'open'

def extract_target(command):
    """Extract target from command."""
    action = extract_action(command)
    if action in command:
        target = command.split(action)[-1].strip()
        return target
    return command.split()[-1] 